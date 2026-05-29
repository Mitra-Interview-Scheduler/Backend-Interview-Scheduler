package com.nemal.dto;

import java.util.List;

public record FeedbackFormDto(
        Long id,
        String name,
        String description,
        boolean isActive,
        Integer versionNumber,
        FeedbackScopesDto scopes,
        List<FeedbackQuestionDto> questions
) {
}
