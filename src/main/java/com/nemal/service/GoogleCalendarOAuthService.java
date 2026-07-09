package com.nemal.service;

import com.google.api.client.googleapis.auth.oauth2.GoogleIdToken;
import com.google.api.client.googleapis.auth.oauth2.GoogleIdTokenVerifier;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.gson.GsonFactory;
import com.nemal.entity.User;
import com.nemal.repository.UserRepository;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

@Service
public class GoogleCalendarOAuthService {

    private static final String OAUTH_STATE_PURPOSE = "google_calendar_connect";
    private static final long STATE_EXPIRATION_MS = 10 * 60 * 1000;

    private final GoogleCalendarTokenService tokenService;
    private final UserRepository userRepository;
    private final String clientId;
    private final String frontendUrl;
    private final String jwtSecret;

    public GoogleCalendarOAuthService(
            GoogleCalendarTokenService tokenService,
            UserRepository userRepository,
            @Value("${google.client.id}") String clientId,
            @Value("${app.frontend.url:http://localhost:5173}") String frontendUrl,
            @Value("${jwt.secret}") String jwtSecret) {
        this.tokenService = tokenService;
        this.userRepository = userRepository;
        this.clientId = clientId;
        this.frontendUrl = frontendUrl.endsWith("/") ? frontendUrl.substring(0, frontendUrl.length() - 1) : frontendUrl;
        this.jwtSecret = jwtSecret;
    }

    public String buildAuthorizationUrl(User user) {
        return buildAuthorizationUrl(user, null);
    }

    public String buildAuthorizationUrl(User user, String returnTo) {
        try {
            String state = generateStateToken(user, sanitizeReturnTo(returnTo));
            return tokenService.buildAuthorizationFlow()
                    .newAuthorizationUrl()
                    .setRedirectUri(tokenService.getRedirectUri())
                    .setState(state)
                    .build();
        } catch (Exception e) {
            throw new RuntimeException("Failed to build Google Calendar authorization URL", e);
        }
    }

    @Transactional
    public String handleCallback(String code, String state) {
        StateClaims stateClaims = validateStateToken(state);
        User user = userRepository.findById(stateClaims.userId())
                .orElseThrow(() -> new RuntimeException("User not found for Google Calendar callback"));

        try {
            var tokenResponse = tokenService.exchangeCode(code);
            String googleEmail = resolveGoogleEmail(tokenResponse.getIdToken());
            if (googleEmail == null || googleEmail.isBlank()) {
                googleEmail = user.getEmail();
            }

            long expiresIn = tokenResponse.getExpiresInSeconds() != null
                    ? tokenResponse.getExpiresInSeconds()
                    : 3600L;

            tokenService.saveTokens(
                    user,
                    googleEmail,
                    tokenResponse.getAccessToken(),
                    tokenResponse.getRefreshToken(),
                    expiresIn);

            String returnPath = stateClaims.returnTo() != null ? stateClaims.returnTo() : "/settings";
            return frontendUrl + returnPath + "?googleCalendar=connected";
        } catch (Exception e) {
            throw new RuntimeException("Failed to complete Google Calendar authorization", e);
        }
    }

    public String buildFailureRedirectUrl(String message) {
        return buildFailureRedirectUrl(message, "/settings");
    }

    public String buildFailureRedirectUrl(String message, String returnTo) {
        String encoded = message != null ? message.replace(" ", "+") : "authorization_failed";
        String returnPath = sanitizeReturnTo(returnTo) != null ? sanitizeReturnTo(returnTo) : "/settings";
        return frontendUrl + returnPath + "?googleCalendar=error&message=" + encoded;
    }

    private String resolveGoogleEmail(String idTokenString) {
        if (idTokenString == null || idTokenString.isBlank()) {
            return null;
        }
        try {
            GoogleIdTokenVerifier verifier = new GoogleIdTokenVerifier.Builder(
                    new NetHttpTransport(), GsonFactory.getDefaultInstance())
                    .setAudience(Collections.singletonList(clientId))
                    .build();
            GoogleIdToken idToken = verifier.verify(idTokenString);
            if (idToken == null) {
                return null;
            }
            return (String) idToken.getPayload().getEmail();
        } catch (Exception e) {
            return null;
        }
    }

    private String generateStateToken(User user, String returnTo) {
        Map<String, Object> claims = new HashMap<>();
        claims.put("purpose", OAUTH_STATE_PURPOSE);
        claims.put("userId", user.getId());
        if (returnTo != null) {
            claims.put("returnTo", returnTo);
        }

        return Jwts.builder()
                .claims(claims)
                .subject(user.getEmail())
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + STATE_EXPIRATION_MS))
                .signWith(getSignInKey())
                .compact();
    }

    private record StateClaims(Long userId, String returnTo) {}

    private StateClaims validateStateToken(String state) {
        try {
            Claims claims = Jwts.parser()
                    .verifyWith(getSignInKey())
                    .build()
                    .parseSignedClaims(state)
                    .getPayload();

            if (!OAUTH_STATE_PURPOSE.equals(claims.get("purpose"))) {
                throw new RuntimeException("Invalid OAuth state purpose");
            }
            Object userId = claims.get("userId");
            Long parsedUserId;
            if (userId instanceof Integer intId) {
                parsedUserId = intId.longValue();
            } else if (userId instanceof Long longId) {
                parsedUserId = longId;
            } else {
                throw new RuntimeException("Invalid OAuth state user id");
            }

            String returnTo = sanitizeReturnTo(claims.get("returnTo", String.class));
            return new StateClaims(parsedUserId, returnTo);
        } catch (Exception e) {
            throw new RuntimeException("Invalid or expired OAuth state", e);
        }
    }

    private String sanitizeReturnTo(String returnTo) {
        if (returnTo == null || returnTo.isBlank()) {
            return null;
        }
        String normalized = returnTo.trim();
        if (!normalized.startsWith("/") || normalized.startsWith("//")) {
            return null;
        }
        return normalized;
    }

    private SecretKey getSignInKey() {
        byte[] keyBytes;
        try {
            keyBytes = Decoders.BASE64.decode(jwtSecret);
        } catch (IllegalArgumentException ex) {
            keyBytes = jwtSecret.getBytes(StandardCharsets.UTF_8);
        }
        return Keys.hmacShaKeyFor(keyBytes);
    }
}
