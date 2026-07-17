package com.nemal.dto;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Request payload for checking whether the selected interviewer(s) have any
 * conflicting Google Calendar events during a proposed interview window.
 * Times are in the caller's timezone (converted to UTC in the controller).
 */
public record ConflictCheckRequestDto(
        List<Long> interviewerIds,
        LocalDateTime startDateTime,
        LocalDateTime endDateTime
) {}
