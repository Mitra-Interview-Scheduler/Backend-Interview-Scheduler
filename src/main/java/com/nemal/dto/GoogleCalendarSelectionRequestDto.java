package com.nemal.dto;

import java.util.List;

public record GoogleCalendarSelectionRequestDto(
        List<String> calendarIds
) {}
