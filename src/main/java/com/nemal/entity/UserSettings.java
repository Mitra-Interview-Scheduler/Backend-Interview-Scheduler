package com.nemal.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "user_settings")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
@ToString(exclude = "user")
public class UserSettings {

    public static final String DEFAULT_TIMEZONE = "UTC";
    public static final String DEFAULT_DATE_FORMAT = "yyyy-MM-dd";
    public static final String DEFAULT_TIME_FORMAT = "HH:mm";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @EqualsAndHashCode.Include
    private Long id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false, unique = true)
    private User user;

    @Column(nullable = false, length = 64)
    @Builder.Default
    private String timezone = DEFAULT_TIMEZONE;

    @Column(name = "preferred_date_format", nullable = false, length = 64)
    @Builder.Default
    private String preferredDateFormat = DEFAULT_DATE_FORMAT;

    @Column(name = "preferred_time_format", nullable = false, length = 64)
    @Builder.Default
    private String preferredTimeFormat = DEFAULT_TIME_FORMAT;

    @Column(name = "timezone_captured", nullable = false)
    @Builder.Default
    private boolean timezoneCaptured = false;

    @Column(name = "email_notifications_enabled", nullable = false)
    @Builder.Default
    private boolean emailNotificationsEnabled = true;
}


