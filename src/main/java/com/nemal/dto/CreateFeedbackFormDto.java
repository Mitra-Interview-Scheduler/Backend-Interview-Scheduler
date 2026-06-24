package com.nemal.dto;

import java.util.List;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;

public record CreateFeedbackFormDto(
        @NotBlank String name,
        String description,
        FeedbackScopesDto scopes,
        @Valid List<CreateFeedbackQuestionDto> questions
) {
}
