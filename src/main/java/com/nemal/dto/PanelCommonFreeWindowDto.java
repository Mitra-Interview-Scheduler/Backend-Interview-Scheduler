package com.nemal.dto;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Overlapping free time shared by every interviewer on a panel.
 */
public record PanelCommonFreeWindowDto(
        LocalDateTime startDateTime,
        LocalDateTime endDateTime,
        List<Long> availabilitySlotIds,
        List<String> interviewerNames
) {}
