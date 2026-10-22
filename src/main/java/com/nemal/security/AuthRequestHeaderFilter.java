package com.nemal.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.lang.NonNull;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * CSRF defence for the cookie-driven auth endpoints (/api/auth/refresh, /logout, ...).
 *
 * Cookie CSRF tokens do not work for this split-site SPA, so instead every POST to
 * /api/auth/** must carry "X-Requested-With: XMLHttpRequest". A browser only lets another
 * site send a custom header after a CORS preflight, which our CORS allow-list rejects, so a
 * forged cross-site form or fetch can no longer refresh, log out or log in a victim.
 */
public class AuthRequestHeaderFilter extends OncePerRequestFilter {

    public static final String HEADER = "X-Requested-With";
    public static final String EXPECTED_VALUE = "XMLHttpRequest";

    @Override
    protected boolean shouldNotFilter(@NonNull HttpServletRequest request) {
        if (!HttpMethod.POST.matches(request.getMethod())) {
            return true;
        }
        String path = request.getRequestURI();
        String context = request.getContextPath();
        if (context != null && !context.isEmpty() && path.startsWith(context)) {
            path = path.substring(context.length());
        }
        return !path.startsWith("/api/auth/");
    }

    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain
    ) throws ServletException, IOException {
        if (!EXPECTED_VALUE.equalsIgnoreCase(request.getHeader(HEADER))) {
            response.setStatus(HttpServletResponse.SC_FORBIDDEN);
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.getWriter().write("{\"message\":\"Missing required request header\"}");
            return;
        }
        filterChain.doFilter(request, response);
    }
}
