package com.nemal.service;

import com.nemal.dto.LoginResponse;
import com.nemal.entity.User;
import com.nemal.entity.UserRefreshToken;
import com.nemal.repository.UserRefreshTokenRepository;
import com.nemal.repository.UserRepository;
import com.nemal.security.JwtService;
import com.nemal.security.RefreshTokenCookieWriter;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.DisabledException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.HexFormat;
import java.util.UUID;

@Service
public class RefreshTokenService {

    private static final SecureRandom RANDOM = new SecureRandom();

    private final UserRefreshTokenRepository refreshTokenRepository;
    private final UserRepository userRepository;
    private final JwtService jwtService;
    private final RefreshTokenCookieWriter cookieWriter;

    public RefreshTokenService(
            UserRefreshTokenRepository refreshTokenRepository,
            UserRepository userRepository,
            JwtService jwtService,
            RefreshTokenCookieWriter cookieWriter
    ) {
        this.refreshTokenRepository = refreshTokenRepository;
        this.userRepository = userRepository;
        this.jwtService = jwtService;
        this.cookieWriter = cookieWriter;
    }

    @Transactional
    public void issueNewSession(User user, HttpServletResponse response) {
        LocalDateTime now = LocalDateTime.now();
        refreshTokenRepository.revokeAllActiveForUser(user, now);
        String rawToken = newRawToken();
        persist(user, rawToken, UUID.randomUUID(), now);
        cookieWriter.write(response, rawToken);
    }

    @Transactional
    public LoginResponse refresh(HttpServletRequest request, HttpServletResponse response) {
        String rawToken = readCookie(request);
        if (rawToken == null || rawToken.isBlank()) {
            cookieWriter.clear(response);
            throw unauthorized();
        }

        UserRefreshToken stored = refreshTokenRepository.findByTokenHash(hash(rawToken)).orElse(null);
        if (stored == null) {
            cookieWriter.clear(response);
            throw unauthorized();
        }

        User user = stored.getUser();
        if (stored.isRevoked()) {
            refreshTokenRepository.revokeAllActiveForUser(user, LocalDateTime.now());
            cookieWriter.clear(response);
            throw unauthorized();
        }
        if (stored.isExpired()) {
            stored.setRevokedAt(LocalDateTime.now());
            cookieWriter.clear(response);
            throw unauthorized();
        }
        if (!user.isEnabled()) {
            refreshTokenRepository.revokeAllActiveForUser(user, LocalDateTime.now());
            cookieWriter.clear(response);
            throw new DisabledException("Account is disabled");
        }

        LocalDateTime now = LocalDateTime.now();
        stored.setRevokedAt(now);
        String nextRaw = newRawToken();
        persist(user, nextRaw, stored.getFamilyId(), now);
        cookieWriter.write(response, nextRaw);
        return LoginResponse.from(jwtService.generateToken(user), user);
    }

    @Transactional
    public void logout(HttpServletRequest request, HttpServletResponse response) {
        String rawToken = readCookie(request);
        if (rawToken != null && !rawToken.isBlank()) {
            refreshTokenRepository.findByTokenHash(hash(rawToken)).ifPresent(stored -> {
                if (!stored.isRevoked()) {
                    stored.setRevokedAt(LocalDateTime.now());
                }
            });
        }
        cookieWriter.clear(response);
    }

    @Transactional
    public void revokeAllForUser(User user) {
        refreshTokenRepository.revokeAllActiveForUser(user, LocalDateTime.now());
    }

    @Transactional
    public void revokeAllForUserId(Long userId) {
        userRepository.findById(userId).ifPresent(this::revokeAllForUser);
    }

    private void persist(User user, String rawToken, UUID familyId, LocalDateTime now) {
        UserRefreshToken token = UserRefreshToken.builder()
                .user(user)
                .tokenHash(hash(rawToken))
                .familyId(familyId)
                .expiresAt(now.plusSeconds(Math.max(1L, cookieWriter.expirationMs() / 1000)))
                .createdAt(now)
                .build();
        refreshTokenRepository.save(token);
    }

    private String readCookie(HttpServletRequest request) {
        Cookie[] cookies = request.getCookies();
        if (cookies == null) {
            return null;
        }
        String name = cookieWriter.cookieName();
        for (Cookie cookie : cookies) {
            if (name.equals(cookie.getName())) {
                return cookie.getValue();
            }
        }
        return null;
    }

    private String newRawToken() {
        byte[] bytes = new byte[64];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    static String hash(String rawToken) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(rawToken.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException("Unable to hash refresh token", e);
        }
    }

    private ResponseStatusException unauthorized() {
        return new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid or expired refresh token");
    }
}
