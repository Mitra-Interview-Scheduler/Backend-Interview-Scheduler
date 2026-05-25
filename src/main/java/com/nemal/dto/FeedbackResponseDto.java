package com.nemal.dto;

import java.time.LocalDateTime;
import java.util.Map;

public record FeedbackResponseDto(
        Long id,
        Long interviewScheduleId,
        Long interviewerId,
        Map<String, Object> responses,
        LocalDateTime submittedAt
) {
}
