package com.nemal.security;

import com.nemal.entity.User;
import com.nemal.entity.UserRefreshToken;
import com.nemal.repository.UserRefreshTokenRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Base64;

@Service
public class RefreshTokenService {

    private final UserRefreshTokenRepository refreshTokenRepository;
    private final long refreshTokenExpirationMs;
    private final SecureRandom secureRandom = new SecureRandom();

    public RefreshTokenService(
            UserRefreshTokenRepository refreshTokenRepository,
            @Value("${auth.refresh-token.expiration-ms:2592000000}") long refreshTokenExpirationMs
    ) {
        this.refreshTokenRepository = refreshTokenRepository;
        this.refreshTokenExpirationMs = refreshTokenExpirationMs;
    }

    @Transactional
    public String issueToken(User user, String ip, String userAgent) {
        String rawToken = newRawToken();
        UserRefreshToken refreshToken = UserRefreshToken.builder()
                .user(user)
                .tokenHash(hash(rawToken))
                .createdAt(LocalDateTime.now())
                .expiresAt(LocalDateTime.now().plusSeconds(refreshTokenExpirationMs / 1000))
                .createdByIp(ip)
                .userAgent(trimUserAgent(userAgent))
                .build();
        refreshTokenRepository.save(refreshToken);
        return rawToken;
    }

    @Transactional
    public TokenRotationResult rotate(String rawToken, String ip, String userAgent) {
        if (rawToken == null || rawToken.isBlank()) {
            throw new BadCredentialsException("Missing refresh token");
        }
        String tokenHash = hash(rawToken);
        UserRefreshToken existing = refreshTokenRepository.findByTokenHashAndRevokedAtIsNull(tokenHash)
                .orElseGet(() -> refreshTokenRepository.findByTokenHash(tokenHash)
                        .orElseThrow(() -> new BadCredentialsException("Invalid refresh token")));

        if (existing.isRevoked()) {
            // Reuse of an already-revoked token indicates compromise. Revoke all active sessions.
            if (existing.getReplacedByTokenHash() != null) {
                revokeAllForUser(existing.getUser().getId());
            }
            throw new BadCredentialsException("Refresh token expired");
        }

        if (existing.isExpired(LocalDateTime.now())) {
            existing.setRevokedAt(LocalDateTime.now());
            refreshTokenRepository.save(existing);
            throw new BadCredentialsException("Refresh token expired");
        }

        if (!existing.getUser().isEnabled()) {
            revokeAllForUser(existing.getUser().getId());
            throw new BadCredentialsException("Account is disabled");
        }

        existing.setRevokedAt(LocalDateTime.now());

        String newRawToken = newRawToken();
        String newHash = hash(newRawToken);
        existing.setReplacedByTokenHash(newHash);

        UserRefreshToken rotated = UserRefreshToken.builder()
                .user(existing.getUser())
                .tokenHash(newHash)
                .createdAt(LocalDateTime.now())
                .expiresAt(LocalDateTime.now().plusSeconds(refreshTokenExpirationMs / 1000))
                .createdByIp(ip)
                .userAgent(trimUserAgent(userAgent))
                .build();

        refreshTokenRepository.save(existing);
        refreshTokenRepository.save(rotated);
        return new TokenRotationResult(existing.getUser(), newRawToken);
    }

    @Transactional
    public void revoke(String rawToken) {
        if (rawToken == null || rawToken.isBlank()) {
            return;
        }
        refreshTokenRepository.findByTokenHash(hash(rawToken)).ifPresent(token -> {
            token.setRevokedAt(LocalDateTime.now());
            refreshTokenRepository.save(token);
        });
    }

    @Transactional
    public void revokeAllForUser(Long userId) {
        LocalDateTime now = LocalDateTime.now();
        for (UserRefreshToken token : refreshTokenRepository.findAllByUserIdAndRevokedAtIsNull(userId)) {
            token.setRevokedAt(now);
        }
    }

    private String newRawToken() {
        byte[] buffer = new byte[64];
        secureRandom.nextBytes(buffer);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(buffer);
    }

    private String hash(String token) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(token.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(hash.length * 2);
            for (byte b : hash) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (Exception e) {
            throw new IllegalStateException("Unable to hash refresh token", e);
        }
    }

    private String trimUserAgent(String userAgent) {
        if (userAgent == null) {
            return null;
        }
        return userAgent.length() <= 512 ? userAgent : userAgent.substring(0, 512);
    }

    public record TokenRotationResult(User user, String newRawToken) {}
}
