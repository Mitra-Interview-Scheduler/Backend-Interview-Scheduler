package com.nemal.dto;

import java.util.List;

public record GoogleCalendarSelectionResponseDto(
        List<String> calendarIds,
        boolean usingCustomSelection
) {}
