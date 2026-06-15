package com.nemal.dto;

import com.nemal.entity.MasterStep;

public record CandidateStepDto(
        Long id,
        String key,
        String label,
        Integer step,
        Integer displayOrder,
        String bgColor,
        String badgeClass,
        String lightClass,
        boolean isClosingStep
) {
    public static CandidateStepDto from(MasterStep step) {
        return new CandidateStepDto(
                step.getId(),
                step.getStatusKey(),
                step.getLabel(),
                step.getStepOrder(),
                step.getDisplayOrder(),
                step.getBgColor(),
                step.getBadgeClass(),
                step.getLightClass(),
                step.isClosingStep()
        );
    }
}
