package com.nemal.dto;

import com.nemal.entity.TechnologyCategory;

public record TechnologyCategoryDto(
        Long id,
        String code,
        String label,
        Integer displayOrder,
        boolean isActive
) {
    public static TechnologyCategoryDto from(TechnologyCategory category) {
        return new TechnologyCategoryDto(
                category.getId(),
                category.getCode(),
                category.getLabel(),
                category.getDisplayOrder(),
                category.isActive()
        );
    }
}
