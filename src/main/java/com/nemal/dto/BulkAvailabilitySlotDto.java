package com.nemal.dto;

import java.util.List;

public record BulkAvailabilitySlotDto(
        List<CreateAvailabilitySlotDto> slots
) {}