package com.nemal.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
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

@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 20)
public class RateLimitFilter extends OncePerRequestFilter {

    private static final long WINDOW_MS = 60_000L;

    private final int authRequestsPerMinute;
    private final int sensitiveWritesPerMinute;
    private final boolean trustForwardedFor;
    private final Map<String, Deque<Long>> hits = new ConcurrentHashMap<>();

    public RateLimitFilter(
            @Value("${security.rate-limit.auth-requests-per-minute:20}") int authRequestsPerMinute,
            @Value("${security.rate-limit.sensitive-writes-per-minute:120}") int sensitiveWritesPerMinute,
            @Value("${security.rate-limit.trust-forwarded-for:false}") boolean trustForwardedFor
    ) {
        this.authRequestsPerMinute = authRequestsPerMinute;
        this.sensitiveWritesPerMinute = sensitiveWritesPerMinute;
        this.trustForwardedFor = trustForwardedFor;
    }

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
        int limit = limitFor(request.getMethod(), path);
        if (limit <= 0) {
            filterChain.doFilter(request, response);
            return;
        }

        String bucket = (limit == authRequestsPerMinute ? "auth:" : "write:") + clientIp(request);
        long now = System.currentTimeMillis();
        long retryAfterSeconds = recordAndRetryAfter(bucket, now, limit);
        if (retryAfterSeconds > 0) {
            response.setStatus(429);
            response.setHeader("Retry-After", String.valueOf(retryAfterSeconds));
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.getWriter().write("{\"message\":\"Too many requests\"}");
            return;
        }

        filterChain.doFilter(request, response);
    }

    private int limitFor(String method, String path) {
        if (path.startsWith("/api/auth/")) {
            return authRequestsPerMinute;
        }
        if (isSensitiveWrite(method, path)) {
            return sensitiveWritesPerMinute;
        }
        return 0;
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

    private String clientIp(HttpServletRequest request) {
        if (trustForwardedFor) {
            String forwarded = request.getHeader("X-Forwarded-For");
            if (forwarded != null && !forwarded.isBlank()) {
                int comma = forwarded.indexOf(',');
                return (comma < 0 ? forwarded : forwarded.substring(0, comma)).trim();
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
