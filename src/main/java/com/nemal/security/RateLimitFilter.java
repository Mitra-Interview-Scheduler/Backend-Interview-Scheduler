package com.nemal.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.concurrent.atomic.AtomicLong;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class RateLimitFilter extends OncePerRequestFilter {

    private final Map<String, Deque<Long>> requestWindows = new ConcurrentHashMap<>();
    private final AtomicLong requestCounter = new AtomicLong(0);
    private final int authRequestsPerMinute;
    private final int sensitiveWriteRequestsPerMinute;
    private final boolean trustForwardedFor;

    public RateLimitFilter(
            @Value("${security.rate-limit.auth-requests-per-minute:20}") int authRequestsPerMinute,
            @Value("${security.rate-limit.sensitive-writes-per-minute:120}") int sensitiveWriteRequestsPerMinute,
            @Value("${security.rate-limit.trust-forwarded-for:false}") boolean trustForwardedFor
    ) {
        this.authRequestsPerMinute = authRequestsPerMinute;
        this.sensitiveWriteRequestsPerMinute = sensitiveWriteRequestsPerMinute;
        this.trustForwardedFor = trustForwardedFor;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String path = request.getRequestURI();
        String method = request.getMethod();
        String clientIp = extractClientIp(request);
        cleanupStaleBucketsPeriodically();

        String bucket = null;
        int limit = 0;
        if (path.startsWith("/api/auth/")) {
            bucket = "auth:" + clientIp;
            limit = authRequestsPerMinute;
        } else if (isSensitiveWrite(path, method)) {
            bucket = "sensitive:" + clientIp;
            limit = sensitiveWriteRequestsPerMinute;
        }

        if (bucket != null && isLimited(bucket, limit)) {
            response.setStatus(429);
            response.setContentType("application/json");
            response.getWriter().write("{\"message\":\"Too many requests\"}");
            return;
        }

        filterChain.doFilter(request, response);
    }

    private boolean isSensitiveWrite(String path, String method) {
        if (!HttpMethod.POST.matches(method) && !HttpMethod.PUT.matches(method)
                && !HttpMethod.PATCH.matches(method) && !HttpMethod.DELETE.matches(method)) {
            return false;
        }
        return path.startsWith("/api/admin/")
                || path.startsWith("/api/candidates/")
                || path.startsWith("/api/feedback/")
                || path.startsWith("/api/interview-requests/")
                || path.startsWith("/api/candidatePipeline/");
    }

    private boolean isLimited(String key, int maxPerMinute) {
        long now = Instant.now().toEpochMilli();
        long cutoff = now - 60_000;
        Deque<Long> timestamps = requestWindows.computeIfAbsent(key, k -> new ArrayDeque<>());
        synchronized (timestamps) {
            while (!timestamps.isEmpty() && timestamps.peekFirst() < cutoff) {
                timestamps.pollFirst();
            }
            if (timestamps.size() >= maxPerMinute) {
                return true;
            }
            timestamps.addLast(now);
            return false;
        }
    }

    private String extractClientIp(HttpServletRequest request) {
        if (!trustForwardedFor) {
            return request.getRemoteAddr();
        }
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }

    private void cleanupStaleBucketsPeriodically() {
        if (requestCounter.incrementAndGet() % 1000 != 0) {
            return;
        }
        long cutoff = Instant.now().toEpochMilli() - 60_000;
        for (Map.Entry<String, Deque<Long>> entry : requestWindows.entrySet()) {
            Deque<Long> timestamps = entry.getValue();
            synchronized (timestamps) {
                while (!timestamps.isEmpty() && timestamps.peekFirst() < cutoff) {
                    timestamps.pollFirst();
                }
                if (timestamps.isEmpty()) {
                    requestWindows.remove(entry.getKey(), timestamps);
                }
            }
        }
    }
}
