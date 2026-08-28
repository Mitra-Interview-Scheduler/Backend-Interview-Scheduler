package com.nemal.repository;

import com.nemal.entity.User;
import com.nemal.entity.UserRefreshToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

public interface UserRefreshTokenRepository extends JpaRepository<UserRefreshToken, Long> {

    Optional<UserRefreshToken> findByTokenHash(String tokenHash);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE UserRefreshToken t SET t.revokedAt = :revokedAt WHERE t.user = :user AND t.revokedAt IS NULL")
    int revokeAllActiveForUser(@Param("user") User user, @Param("revokedAt") LocalDateTime revokedAt);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE UserRefreshToken t SET t.revokedAt = :revokedAt WHERE t.familyId = :familyId AND t.revokedAt IS NULL")
    int revokeAllActiveForFamily(@Param("familyId") UUID familyId, @Param("revokedAt") LocalDateTime revokedAt);
}
