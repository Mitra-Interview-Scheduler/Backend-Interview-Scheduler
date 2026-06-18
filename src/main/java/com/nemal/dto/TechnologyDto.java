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
        return new TechnologyDto(
                tech.getId(),
                tech.getCode(),
                tech.getName(),
                TechnologyCategoryDto.from(tech.getCategory()),
                tech.isActive()
        );
    }
}
