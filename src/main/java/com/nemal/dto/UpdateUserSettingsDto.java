package com.nemal.dto;

public record UpdateUserSettingsDto(
        String timezone,
        String preferredDateFormat,
        String preferredTimeFormat
) {}

