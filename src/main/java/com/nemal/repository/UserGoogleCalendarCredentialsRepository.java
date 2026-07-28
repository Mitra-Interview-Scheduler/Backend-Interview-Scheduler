package com.nemal.repository;

import com.nemal.entity.UserGoogleCalendarCredentials;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface UserGoogleCalendarCredentialsRepository extends JpaRepository<UserGoogleCalendarCredentials, Long> {
    Optional<UserGoogleCalendarCredentials> findByUserId(Long userId);
    void deleteByUserId(Long userId);
}
