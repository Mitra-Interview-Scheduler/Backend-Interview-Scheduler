package com.nemal.dto;

import java.util.List;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateFeedbackQuestionDto(
        Integer order,
        @NotBlank @Size(max = 2000, message = "Question label must be at most 2000 characters") String label,
        Long categoryId,
        String category,
        @NotBlank String type,
        boolean required,
        boolean commentsEnabled,
        @Size(max = 255, message = "Placeholder must be at most 255 characters") String placeholder,
        @Size(max = 500, message = "Help text must be at most 500 characters") String helpText,
        List<FeedbackOptionDto> options
) {
}
