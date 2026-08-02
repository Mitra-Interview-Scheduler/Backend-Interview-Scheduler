package com.nemal.dto;

import java.util.List;

public record AvailabilityRangeResponseDto(
        List<AvailabilitySlotDto> items,
        List<GoogleCalendarExternalEventDto> googleExternalEvents
) {}
