package com.nemal.security;

import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

import java.time.Duration;

@Component
public class RefreshTokenCookieWriter {

    private final String cookieName;
    private final long expirationMs;
    private final boolean secure;
    private final String sameSite;

    public RefreshTokenCookieWriter(
            @Value("${auth.refresh-token.cookie-name:mitra_refresh_token}") String cookieName,
            @Value("${auth.refresh-token.expiration-ms:2592000000}") long expirationMs,
            @Value("${auth.refresh-token.secure:false}") boolean secure,
            @Value("${auth.refresh-token.same-site:Strict}") String sameSite
    ) {
        this.cookieName = cookieName;
        this.expirationMs = expirationMs;
        this.secure = secure;
        this.sameSite = sameSite;
    }

    public String cookieName() {
        return cookieName;
    }

    public long expirationMs() {
        return expirationMs;
    }

    public void write(HttpServletResponse response, String rawToken) {
        response.addHeader(HttpHeaders.SET_COOKIE, buildCookie(rawToken, Duration.ofMillis(expirationMs)).toString());
    }

    public void clear(HttpServletResponse response) {
        response.addHeader(HttpHeaders.SET_COOKIE, buildCookie("", Duration.ZERO).toString());
    }

    private ResponseCookie buildCookie(String value, Duration maxAge) {
        return ResponseCookie.from(cookieName, value == null ? "" : value)
                .httpOnly(true)
                .secure(secure)
                .path("/api/auth")
                .maxAge(maxAge)
                .sameSite(sameSite)
                .build();
    }
}
