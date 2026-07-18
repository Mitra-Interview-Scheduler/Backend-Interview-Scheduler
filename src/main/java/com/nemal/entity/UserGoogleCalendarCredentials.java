package com.nemal.entity;

import jakarta.persistence.*;
import lombok.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;

@Entity
@Table(name = "user_google_calendar_credentials")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EntityListeners(AuditingEntityListener.class)
public class UserGoogleCalendarCredentials {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false, unique = true)
    private User user;

    @Column(name = "google_account_email", nullable = false)
    private String googleAccountEmail;

    @Column(name = "encrypted_access_token", nullable = false, columnDefinition = "TEXT")
    private String encryptedAccessToken;

    @Column(name = "encrypted_refresh_token", nullable = false, columnDefinition = "TEXT")
    private String encryptedRefreshToken;

    @Column(name = "token_expires_at", nullable = false)
    private LocalDateTime tokenExpiresAt;

    @Column(nullable = false, length = 500)
    private String scopes;

    /**
     * JSON array of Google calendar IDs the user wants Mitra to read.
     * Null means "use Google's selected calendars" (legacy default).
     * Empty JSON array "[]" means show no Google calendars on availability.
     */
    @Column(name = "selected_calendar_ids", columnDefinition = "TEXT")
    private String selectedCalendarIds;

    /**
     * ID of the dedicated "Interview Scheduler" secondary calendar in the user's
     * Google account where Mitra writes its events. Null until first resolved
     * (created or found by name) lazily.
     */
    @Column(name = "app_calendar_id", length = 255)
    private String appCalendarId;

    @CreatedDate
    @Column(name = "connected_at", nullable = false, updatable = false)
    private LocalDateTime connectedAt;

    @LastModifiedDate
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}
