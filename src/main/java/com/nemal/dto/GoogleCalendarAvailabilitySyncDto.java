package com.nemal.dto;

public record GoogleCalendarAvailabilitySyncDto(
        int syncedCount,
        int attemptedCount
) {}
