package com.nemal.dto;

import java.util.List;
import jakarta.validation.constraints.NotBlank;

public record CreateFeedbackFormDto(
        @NotBlank String name,
        String description,
        FeedbackScopesDto scopes,
        List<CreateFeedbackQuestionDto> questions
) {
}
