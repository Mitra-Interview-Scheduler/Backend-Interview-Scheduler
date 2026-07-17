package com.nemal.service;

import com.nemal.dto.UpdateUserSettingsDto;
import com.nemal.dto.UserSettingsDto;
import com.nemal.entity.User;
import com.nemal.entity.UserSettings;
import com.nemal.repository.UserRepository;
import com.nemal.repository.UserSettingsRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

@Service
public class UserSettingsService {

    private static final Logger logger = LoggerFactory.getLogger(UserSettingsService.class);

    private final UserRepository userRepository;
    private final UserSettingsRepository userSettingsRepository;

    public UserSettingsService(UserRepository userRepository, UserSettingsRepository userSettingsRepository) {
        this.userRepository = userRepository;
        this.userSettingsRepository = userSettingsRepository;
    }

    @Transactional
    public UserSettingsDto getMySettings(User user) {
        return UserSettingsDto.from(getOrCreateSettings(user));
    }

    @Transactional
    public UserSettingsDto updateMySettings(User user, UpdateUserSettingsDto dto) {
        UserSettings settings = getOrCreateSettings(user);

        if (dto.timezone() != null && !dto.timezone().isBlank()) {
            settings.setTimezone(validateTimezone(dto.timezone()));
            settings.setTimezoneCaptured(true);
        }
        if (dto.preferredDateFormat() != null && !dto.preferredDateFormat().isBlank()) {
            settings.setPreferredDateFormat(validateDateFormat(dto.preferredDateFormat()));
        }
        if (dto.preferredTimeFormat() != null && !dto.preferredTimeFormat().isBlank()) {
            settings.setPreferredTimeFormat(validateTimeFormat(dto.preferredTimeFormat()));
        }
        if (dto.emailNotificationsEnabled() != null) {
            settings.setEmailNotificationsEnabled(dto.emailNotificationsEnabled());
        }

        return UserSettingsDto.from(userSettingsRepository.save(settings));
    }

    /**
     * Resolves the timezone used for Google Calendar event times.
     * Uses the user's saved preference; falls back to UTC when missing or invalid.
     */
    @Transactional(readOnly = true)
    public String resolveTimezoneForCalendarSync(User user) {
        UserSettings settings = getOrCreateSettings(user);
        return sanitizeTimezoneForCalendar(user.getId(), settings.getTimezone());
    }

    @Transactional
    public UserSettings ensureSettingsOnFirstLogin(User user, String browserTimezone) {
        UserSettings settings = userSettingsRepository.findByUserId(user.getId())
                .orElseGet(() -> createDefaultSettings(user, browserTimezone));

        if (!settings.isTimezoneCaptured() && browserTimezone != null && !browserTimezone.isBlank()) {
            settings.setTimezone(validateTimezone(browserTimezone));
            settings.setTimezoneCaptured(true);
            settings = userSettingsRepository.save(settings);
        }

        return settings;
    }

    private UserSettings getOrCreateSettings(User user) {
        return userSettingsRepository.findByUserId(user.getId())
                .orElseGet(() -> createDefaultSettings(user));
    }

    private UserSettings createDefaultSettings(User user) {
        return createDefaultSettings(user, null);
    }

    private UserSettings createDefaultSettings(User user, String browserTimezone) {
        User managedUser = userRepository.findById(user.getId())
                .orElseThrow(() -> new RuntimeException("User not found"));

        UserSettings settings = UserSettings.builder()
                .user(managedUser)
                .timezone(resolveTimezone(browserTimezone))
                .preferredDateFormat(UserSettings.DEFAULT_DATE_FORMAT)
                .preferredTimeFormat(UserSettings.DEFAULT_TIME_FORMAT)
                .timezoneCaptured(browserTimezone != null && !browserTimezone.isBlank())
                .emailNotificationsEnabled(true)
                .build();

        settings = userSettingsRepository.save(settings);
        managedUser.setSettings(settings);
        return settings;
    }

    private String resolveTimezone(String browserTimezone) {
        if (browserTimezone == null || browserTimezone.isBlank()) {
            return UserSettings.DEFAULT_TIMEZONE;
        }
        return validateTimezone(browserTimezone);
    }

    private String validateTimezone(String timezone) {
        String normalized = timezone.trim();
        try {
            ZoneId.of(normalized);
            return normalized;
        } catch (Exception ex) {
            throw new RuntimeException("Invalid timezone: " + timezone);
        }
    }

    private String validateDateFormat(String format) {
        String normalized = format.trim();
        try {
            DateTimeFormatter.ofPattern(normalized);
            return normalized;
        } catch (IllegalArgumentException ex) {
            throw new RuntimeException("Invalid preferred date format: " + format);
        }
    }

    private String validateTimeFormat(String format) {
        String normalized = format.trim();
        try {
            DateTimeFormatter.ofPattern(normalized);
            return normalized;
        } catch (IllegalArgumentException ex) {
            throw new RuntimeException("Invalid preferred time format: " + format);
        }
    }

    private String sanitizeTimezoneForCalendar(Long userId, String timezone) {
        if (timezone == null || timezone.isBlank()) {
            logger.warn(
                    "User {} has no timezone configured for Google Calendar sync; using {}",
                    userId,
                    UserSettings.DEFAULT_TIMEZONE);
            return UserSettings.DEFAULT_TIMEZONE;
        }

        String normalized = timezone.trim();
        try {
            ZoneId.of(normalized);
            return normalized;
        } catch (Exception ex) {
            logger.warn(
                    "User {} has invalid timezone '{}' for Google Calendar sync; using {} ({})",
                    userId,
                    timezone,
                    UserSettings.DEFAULT_TIMEZONE,
                    ex.getMessage());
            return UserSettings.DEFAULT_TIMEZONE;
        }
    }
}





