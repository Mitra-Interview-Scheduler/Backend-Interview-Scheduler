package com.nemal.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

/**
 * Sliding-window rate limits (per minute):
 * - login/register: per client IP, low, to slow password guessing;
 * - Google sign-in: per client IP, higher — Google verifies the token, so it cannot be
 *   brute-forced, and a whole office signing in at 9am shares one IP;
 * - refresh/logout: per client IP, generous (page loads and token renewal);
 * - sensitive writes: per signed-in user, falling back to IP, so colleagues behind the
 *   same office IP do not use up each other's allowance.
 * GET requests to /api/auth/** (csrf, verify) are never limited.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 20)
public class RateLimitFilter extends OncePerRequestFilter {

    private static final long WINDOW_MS = 60_000L;

    private final int authRequestsPerMinute;
    private final int googleRequestsPerMinute;
    private final int sessionRequestsPerMinute;
    private final int sensitiveWritesPerMinute;
    private final boolean trustForwardedFor;
    /** Maps a bearer token to its verified subject (email), or null when invalid. */
    private final Function<String, String> bearerSubjectResolver;
    private final Map<String, Deque<Long>> hits = new ConcurrentHashMap<>();

    @Autowired
    public RateLimitFilter(
            @Value("${security.rate-limit.auth-requests-per-minute:20}") int authRequestsPerMinute,
            @Value("${security.rate-limit.google-requests-per-minute:60}") int googleRequestsPerMinute,
            @Value("${security.rate-limit.session-requests-per-minute:120}") int sessionRequestsPerMinute,
            @Value("${security.rate-limit.sensitive-writes-per-minute:120}") int sensitiveWritesPerMinute,
            @Value("${security.rate-limit.trust-forwarded-for:false}") boolean trustForwardedFor,
            JwtService jwtService
    ) {
        this(authRequestsPerMinute, googleRequestsPerMinute, sessionRequestsPerMinute,
                sensitiveWritesPerMinute, trustForwardedFor, token -> verifiedSubject(jwtService, token));
    }

    RateLimitFilter(
            int authRequestsPerMinute,
            int googleRequestsPerMinute,
            int sessionRequestsPerMinute,
            int sensitiveWritesPerMinute,
            boolean trustForwardedFor,
            Function<String, String> bearerSubjectResolver
    ) {
        this.authRequestsPerMinute = authRequestsPerMinute;
        this.googleRequestsPerMinute = googleRequestsPerMinute;
        this.sessionRequestsPerMinute = sessionRequestsPerMinute;
        this.sensitiveWritesPerMinute = sensitiveWritesPerMinute;
        this.trustForwardedFor = trustForwardedFor;
        this.bearerSubjectResolver = bearerSubjectResolver;
    }

    private record Limit(String bucket, int perMinute, boolean perUser) {}

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return HttpMethod.OPTIONS.matches(request.getMethod());
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        String path = servletPath(request);
        Limit limit = limitFor(request.getMethod(), path);
        if (limit == null || limit.perMinute() <= 0) {
            filterChain.doFilter(request, response);
            return;
        }

        String bucket = limit.bucket() + ":" + clientKey(request, limit.perUser());
        long now = System.currentTimeMillis();
        long retryAfterSeconds = recordAndRetryAfter(bucket, now, limit.perMinute());
        if (retryAfterSeconds > 0) {
            response.setStatus(429);
            response.setHeader("Retry-After", String.valueOf(retryAfterSeconds));
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.getWriter().write("{\"message\":\"Too many requests. Please try again in "
                    + retryAfterSeconds + " seconds.\",\"retryAfterSeconds\":" + retryAfterSeconds + "}");
            return;
        }

        filterChain.doFilter(request, response);
    }

    private Limit limitFor(String method, String path) {
        if (path.startsWith("/api/auth/")) {
            if (!HttpMethod.POST.matches(method)) {
                // GET /api/auth/csrf and /verify run on every page load; never throttle them.
                return null;
            }
            if (path.equals("/api/auth/refresh") || path.equals("/api/auth/logout")) {
                return new Limit("session", sessionRequestsPerMinute, false);
            }
            if (path.equals("/api/auth/google")) {
                return new Limit("google", googleRequestsPerMinute, false);
            }
            // Password endpoints: login, register (and any other auth POST).
            return new Limit("auth", authRequestsPerMinute, false);
        }
        if (isSensitiveWrite(method, path)) {
            return new Limit("write", sensitiveWritesPerMinute, true);
        }
        return null;
    }

    private boolean isSensitiveWrite(String method, String path) {
        if (!HttpMethod.POST.matches(method)
                && !HttpMethod.PUT.matches(method)
                && !HttpMethod.PATCH.matches(method)
                && !HttpMethod.DELETE.matches(method)) {
            return false;
        }
        return path.equals("/api/candidates")
                || path.startsWith("/api/candidates/")
                || path.startsWith("/api/admin/")
                || path.startsWith("/api/feedback/")
                || path.startsWith("/api/interview-requests/")
                || path.startsWith("/api/candidatePipeline/")
                || path.startsWith("/api/hr/");
    }

    private long recordAndRetryAfter(String bucket, long now, int limit) {
        Deque<Long> timestamps = hits.computeIfAbsent(bucket, key -> new ArrayDeque<>());
        synchronized (timestamps) {
            long windowStart = now - WINDOW_MS;
            while (!timestamps.isEmpty() && timestamps.peekFirst() < windowStart) {
                timestamps.removeFirst();
            }
            if (timestamps.size() >= limit) {
                long oldest = timestamps.peekFirst();
                return Math.max(1L, (oldest + WINDOW_MS - now + 999) / 1000);
            }
            timestamps.addLast(now);
            return 0L;
        }
    }

    private String clientKey(HttpServletRequest request, boolean perUser) {
        if (perUser) {
            String subject = bearerSubject(request);
            if (subject != null) {
                return "user:" + subject.toLowerCase();
            }
        }
        return "ip:" + clientIp(request);
    }

    private String bearerSubject(HttpServletRequest request) {
        String authHeader = request.getHeader("Authorization");
        if (authHeader == null || !authHeader.regionMatches(true, 0, "Bearer ", 0, 7)) {
            return null;
        }
        String token = authHeader.substring(7).trim();
        if (token.isEmpty() || bearerSubjectResolver == null) {
            return null;
        }
        String subject = bearerSubjectResolver.apply(token);
        return subject == null || subject.isBlank() ? null : subject;
    }

    private static String verifiedSubject(JwtService jwtService, String token) {
        try {
            // Signature and expiry are checked here, so a forged token cannot pick its own bucket.
            return jwtService.extractUsername(token);
        } catch (RuntimeException ex) {
            return null;
        }
    }

    private String clientIp(HttpServletRequest request) {
        if (trustForwardedFor) {
            // Use the right-most entry: it is appended by our own reverse proxy. Left-most
            // entries come from the client and can be forged to dodge the limit.
            String forwarded = request.getHeader("X-Forwarded-For");
            if (forwarded != null && !forwarded.isBlank()) {
                String[] hops = forwarded.split(",");
                String lastHop = hops[hops.length - 1].trim();
                if (!lastHop.isEmpty()) {
                    return lastHop;
                }
            }
        }
        String remote = request.getRemoteAddr();
        return remote == null || remote.isBlank() ? "unknown" : remote;
    }

    private String servletPath(HttpServletRequest request) {
        String uri = request.getRequestURI();
        String context = request.getContextPath();
        if (context != null && !context.isEmpty() && uri.startsWith(context)) {
            uri = uri.substring(context.length());
        }
        return uri.isEmpty() ? "/" : uri;
    }

    @Scheduled(fixedRate = 60_000)
    void evictIdleBuckets() {
        evictIdleBuckets(System.currentTimeMillis());
    }

    void evictIdleBuckets(long now) {
        Iterator<Map.Entry<String, Deque<Long>>> iterator = hits.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<String, Deque<Long>> entry = iterator.next();
            Deque<Long> timestamps = entry.getValue();
            synchronized (timestamps) {
                while (!timestamps.isEmpty() && timestamps.peekFirst() < now - WINDOW_MS) {
                    timestamps.removeFirst();
                }
                if (timestamps.isEmpty()) {
                    iterator.remove();
                }
            }
        }
    }
}
