package com.nemal.dto;

import com.nemal.entity.UserSettings;

public record UserSettingsDto(
        Long id,
        Long userId,
        String timezone,
        String preferredDateFormat,
        String preferredTimeFormat
) {
    public static UserSettingsDto from(UserSettings settings) {
        if (settings == null) {
            return null;
        }

        return new UserSettingsDto(
                settings.getId(),
                settings.getUser() != null ? settings.getUser().getId() : null,
                settings.getTimezone(),
                settings.getPreferredDateFormat(),
                settings.getPreferredTimeFormat()
        );
    }
}

