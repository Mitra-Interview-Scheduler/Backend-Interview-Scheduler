package com.nemal.dto;

import java.time.LocalDateTime;

/**
 * Payload sent by the interviewer when editing an AVAILABLE slot.
 * Only time range and description can be changed.
 */
public record UpdateAvailabilitySlotDto(
        LocalDateTime startDateTime,
        LocalDateTime endDateTime,
        LocalDateTime currentTime,
        String description
) {}