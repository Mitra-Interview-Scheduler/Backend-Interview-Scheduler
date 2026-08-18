package com.nemal.repository;

import com.nemal.entity.UserRefreshToken;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;

import java.util.List;
import java.util.Optional;

public interface UserRefreshTokenRepository extends JpaRepository<UserRefreshToken, Long> {
    Optional<UserRefreshToken> findByTokenHash(String tokenHash);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<UserRefreshToken> findByTokenHashAndRevokedAtIsNull(String tokenHash);
    List<UserRefreshToken> findAllByUserIdAndRevokedAtIsNull(Long userId);
    void deleteByUserId(Long userId);
}
