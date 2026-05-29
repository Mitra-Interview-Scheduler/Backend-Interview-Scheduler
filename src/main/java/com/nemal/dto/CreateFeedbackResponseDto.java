package com.nemal.dto;

import java.util.Map;

public record CreateFeedbackResponseDto(
        Long interviewScheduleId,
        Long feedbackFormId,
        Map<String, Object> responses,
        String submittedAt
) {
}
