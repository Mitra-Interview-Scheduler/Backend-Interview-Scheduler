package com.nemal.dto;

import java.time.OffsetDateTime;

/**
 * @param grantExpiresAt     when the Google OAuth grant (not the hourly access token) expires, as a
 *                           UTC timestamp with offset so browsers parse it unambiguously
 * @param grantExpiringSoon  true when that grant expires within 2 days (or already has)
 */
public record GoogleCalendarStatusDto(
        boolean connected,
        String googleAccountEmail,
        boolean required,
        OffsetDateTime grantExpiresAt,
        boolean grantExpiringSoon
) {
    public static GoogleCalendarStatusDto disconnected(boolean required) {
        return new GoogleCalendarStatusDto(false, null, required, null, false);
    }

    public static GoogleCalendarStatusDto connected(
            String googleAccountEmail,
            boolean required,
            OffsetDateTime grantExpiresAt,
            boolean grantExpiringSoon
    ) {
        return new GoogleCalendarStatusDto(true, googleAccountEmail, required, grantExpiresAt, grantExpiringSoon);
    }
}
