package com.nemal.dto;

public record CreateInterviewTypeDto(
        String code,
        String label,
        String description,
        Boolean active,
        Integer displayOrder,
        String roundStatusKey,
        String cancelRestoreStatusKey,
        InterviewTypeFilterRulesDto filterRules
) {}
