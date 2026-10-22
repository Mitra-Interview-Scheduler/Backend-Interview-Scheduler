package com.nemal.repository;

import com.nemal.entity.UserGoogleCalendarCredentials;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface UserGoogleCalendarCredentialsRepository extends JpaRepository<UserGoogleCalendarCredentials, Long> {
    Optional<UserGoogleCalendarCredentials> findByUserId(Long userId);
    void deleteByUserId(Long userId);
    boolean existsByUserId(Long userId);

    @Query("SELECT c.user.id FROM UserGoogleCalendarCredentials c")
    List<Long> findAllConnectedUserIds();

    @Query("""
            SELECT c FROM UserGoogleCalendarCredentials c
            JOIN FETCH c.user u
            """)
    List<UserGoogleCalendarCredentials> findAllWithUser();
}
