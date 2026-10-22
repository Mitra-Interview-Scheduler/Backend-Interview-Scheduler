package com.nemal.dto;

import java.time.LocalDateTime;

public record GoogleCalendarStatusDto(
        boolean connected,
        String googleAccountEmail,
        boolean required,
        LocalDateTime tokenExpiresAt,
        boolean tokenExpiringSoon
) {
    public static GoogleCalendarStatusDto disconnected(boolean required) {
        return new GoogleCalendarStatusDto(false, null, required, null, false);
    }

    public static GoogleCalendarStatusDto connected(
            String googleAccountEmail,
            boolean required,
            LocalDateTime tokenExpiresAt,
            boolean tokenExpiringSoon
    ) {
        return new GoogleCalendarStatusDto(true, googleAccountEmail, required, tokenExpiresAt, tokenExpiringSoon);
    }
}
