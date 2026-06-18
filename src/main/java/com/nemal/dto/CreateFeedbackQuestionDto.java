package com.nemal.dto;

import java.util.List;
import jakarta.validation.constraints.NotBlank;

public record CreateFeedbackQuestionDto(
        Integer order,
        @NotBlank String label,
        Long categoryId,
        String category,
        @NotBlank String type,
        boolean required,
        boolean commentsEnabled,
        String placeholder,
        String helpText,
        List<FeedbackOptionDto> options
) {
}
