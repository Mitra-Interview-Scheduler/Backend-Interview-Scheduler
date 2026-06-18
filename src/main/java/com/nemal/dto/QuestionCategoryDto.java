package com.nemal.dto;

import com.nemal.entity.QuestionCategory;

public record QuestionCategoryDto(
        Long id,
        String code,
        String label,
        Integer displayOrder,
        boolean isActive,
        boolean isSystem
) {
    public static QuestionCategoryDto from(QuestionCategory category) {
        return new QuestionCategoryDto(
                category.getId(),
                category.getCode(),
                category.getLabel(),
                category.getDisplayOrder(),
                category.isActive(),
                category.isSystem()
        );
    }
}
