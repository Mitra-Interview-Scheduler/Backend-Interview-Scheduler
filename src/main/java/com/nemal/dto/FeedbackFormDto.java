package com.nemal.dto;

import java.util.List;

public record FeedbackFormDto(
        Long id,
        String name,
        String description,
        FeedbackScopesDto scopes,
        List<FeedbackQuestionDto> questions
) {
}
