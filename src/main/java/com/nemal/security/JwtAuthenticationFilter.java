package com.nemal.security;

import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.lang.NonNull;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final Logger logger = LoggerFactory.getLogger(JwtAuthenticationFilter.class);

    private final JwtService jwtService;
    private final UserDetailsService userDetailsService;

    public JwtAuthenticationFilter(JwtService jwtService, UserDetailsService userDetailsService) {
        this.jwtService = jwtService;
        this.userDetailsService = userDetailsService;
    }

    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain
    ) throws ServletException, IOException {
        final String authHeader = request.getHeader("Authorization");
        if (authHeader == null || !authHeader.regionMatches(true, 0, "Bearer ", 0, 7)) {
            filterChain.doFilter(request, response);
            return;
        }

        final String jwt = authHeader.substring(7).trim();
        if (jwt.isEmpty()) {
            writeUnauthorized(response, "Invalid or expired token");
            return;
        }

        // If the client sent a Bearer token, never continue as anonymous — that
        // surfaces as a misleading "Authentication required" on admin routes.
        try {
            final String userEmail = jwtService.extractUsername(jwt);
            if (userEmail == null || userEmail.isBlank()) {
                writeUnauthorized(response, "Invalid or expired token");
                return;
            }

            UserDetails userDetails = this.userDetailsService.loadUserByUsername(userEmail);
            if (!jwtService.isTokenValid(jwt, userDetails)) {
                writeUnauthorized(response, "Invalid or expired token");
                return;
            }
            if (!userDetails.isEnabled()) {
                writeUnauthorized(response, "Account is disabled");
                return;
            }

            UsernamePasswordAuthenticationToken authToken = new UsernamePasswordAuthenticationToken(
                    userDetails, null, userDetails.getAuthorities());
            authToken.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
            SecurityContextHolder.getContext().setAuthentication(authToken);
        } catch (UsernameNotFoundException ex) {
            SecurityContextHolder.clearContext();
            writeUnauthorized(response, "Invalid or expired token");
            return;
        } catch (JwtException | IllegalArgumentException ex) {
            SecurityContextHolder.clearContext();
            logger.warn("JWT rejected for {} {}: {}", request.getMethod(), request.getRequestURI(), ex.getMessage());
            writeUnauthorized(response, "Invalid or expired token");
            return;
        } catch (RuntimeException ex) {
            SecurityContextHolder.clearContext();
            logger.warn("JWT processing failed for {} {}: {}", request.getMethod(), request.getRequestURI(), ex.toString());
            writeUnauthorized(response, "Invalid or expired token");
            return;
        }

        filterChain.doFilter(request, response);
    }

    private void writeUnauthorized(HttpServletResponse response, String message) throws IOException {
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType("application/json");
        response.getWriter().write("{\"message\":\"" + message + "\"}");
    }
}
