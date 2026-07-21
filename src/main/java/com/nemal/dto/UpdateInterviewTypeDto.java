package com.nemal.dto;

public record UpdateInterviewTypeDto(
        String label,
        String description,
        Boolean active,
        Integer displayOrder,
        String roundStatusKey,
        String cancelRestoreStatusKey,
        InterviewTypeFilterRulesDto filterRules
) {}
