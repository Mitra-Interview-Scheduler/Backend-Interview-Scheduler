package com.nemal.dto;

import java.util.List;

public record FeedbackQuestionDto(
        Long id,
        Integer order,
        String label,
        Long categoryId,
        String categoryCode,
        String category,
        String type,
        boolean required,
        boolean commentsEnabled,
        String placeholder,
        String helpText,
        List<FeedbackOptionDto> options
) {
}
