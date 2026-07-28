package com.nemal.dto;

import java.time.LocalDateTime;

public record CreateInterviewPostponeRequestDto(
        String reason,
        LocalDateTime preferredStartDateTime,
        LocalDateTime preferredEndDateTime
) {}
