package com.nemal.dto;

import java.util.Map;

public record CreateFeedbackResponseDto(
        Long interviewScheduleId,
        Map<String, Object> responses,
        String submittedAt
) {
}
