package com.nemal.dto;

import com.nemal.entity.InterviewType;
import com.nemal.enums.InterviewerFilterMode;

import java.util.ArrayList;
import java.util.List;

public record InterviewTypeDto(
        Long id,
        String code,
        String label,
        String description,
        boolean active,
        Integer displayOrder,
        boolean isSystem,
        String roundStatusKey,
        String cancelRestoreStatusKey,
        InterviewTypeFilterRulesDto filterRules
) {
    public static InterviewTypeDto from(InterviewType type) {
        if (type == null) {
            return null;
        }
        return new InterviewTypeDto(
                type.getId(),
                type.getCode(),
                type.getLabel(),
                type.getDescription(),
                type.isActive(),
                type.getDisplayOrder(),
                type.isSystem(),
                type.getRoundStatusKey(),
                type.getCancelRestoreStatusKey(),
                toFilterRules(type)
        );
    }

    private static InterviewTypeFilterRulesDto toFilterRules(InterviewType type) {
        return new InterviewTypeFilterRulesDto(
                modeOrDefault(type.getDepartmentFilterMode(), InterviewerFilterMode.SAME_AS_CANDIDATE),
                type.getFixedDepartmentId(),
                type.getMinYearsExperience(),
                modeOrDefault(type.getTierFilterMode(), InterviewerFilterMode.SAME_AS_CANDIDATE),
                type.getFixedMinTierId(),
                modeOrDefault(type.getDesignationFilterMode(), InterviewerFilterMode.SAME_AS_CANDIDATE),
                type.getFixedMinDesignationId(),
                modeOrDefault(type.getDomainFilterMode(), InterviewerFilterMode.SAME_AS_CANDIDATE),
                listOrEmpty(type.getFixedDomainIds()),
                modeOrDefault(type.getCategoryFilterMode(), InterviewerFilterMode.NONE),
                listOrEmpty(type.getFixedCategoryIds()),
                modeOrDefault(type.getTechnologyFilterMode(), InterviewerFilterMode.SAME_AS_CANDIDATE),
                listOrEmpty(type.getFixedTechnologyIds())
        );
    }

    private static InterviewerFilterMode modeOrDefault(InterviewerFilterMode mode, InterviewerFilterMode fallback) {
        return mode != null ? mode : fallback;
    }

    private static List<Long> listOrEmpty(java.util.Set<Long> ids) {
        return ids == null || ids.isEmpty() ? List.of() : new ArrayList<>(ids);
    }
}
