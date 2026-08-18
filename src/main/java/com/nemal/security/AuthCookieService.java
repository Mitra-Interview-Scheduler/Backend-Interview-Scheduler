package com.nemal.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Service;

@Service
public class AuthCookieService {

    private final String refreshCookieName;
    private final long refreshTokenExpirationMs;
    private final boolean secureCookie;
    private final String sameSite;

    public AuthCookieService(
            @Value("${auth.refresh-token.cookie-name:mitra_refresh_token}") String refreshCookieName,
            @Value("${auth.refresh-token.expiration-ms:2592000000}") long refreshTokenExpirationMs,
            @Value("${auth.refresh-token.secure:false}") boolean secureCookie,
            @Value("${auth.refresh-token.same-site:Strict}") String sameSite
    ) {
        this.refreshCookieName = refreshCookieName;
        this.refreshTokenExpirationMs = refreshTokenExpirationMs;
        this.secureCookie = secureCookie;
        this.sameSite = sameSite;
    }

    public void writeRefreshCookie(HttpServletResponse response, String refreshToken) {
        ResponseCookie cookie = ResponseCookie.from(refreshCookieName, refreshToken)
                .httpOnly(true)
                .secure(secureCookie)
                .path("/")
                .sameSite(sameSite)
                .maxAge(refreshTokenExpirationMs / 1000)
                .build();
        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
    }

    public void clearRefreshCookie(HttpServletResponse response) {
        ResponseCookie cookie = ResponseCookie.from(refreshCookieName, "")
                .httpOnly(true)
                .secure(secureCookie)
                .path("/")
                .sameSite(sameSite)
                .maxAge(0)
                .build();
        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
    }

    public String readRefreshToken(HttpServletRequest request) {
        if (request.getCookies() == null) {
            return null;
        }
        for (var cookie : request.getCookies()) {
            if (refreshCookieName.equals(cookie.getName())) {
                return cookie.getValue();
            }
        }
        return null;
    }
}
