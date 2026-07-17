package com.nemal.dto;

import com.nemal.entity.Technology;

public record TechnologyDto(
        Long id,
        String code,
        String name,
        TechnologyCategoryDto category,
        boolean isActive
) {
    public static TechnologyDto from(Technology tech) {
        if (tech == null) {
            return null;
        }
        return new TechnologyDto(
                tech.getId(),
                tech.getCode(),
                tech.getName(),
                tech.getCategory() != null ? TechnologyCategoryDto.from(tech.getCategory()) : null,
                tech.isActive()
        );
    }
}
