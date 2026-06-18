package com.nemal.dto;

public record TechnologySimpleDto(
        Long id,
        String code,
        String name,
        String categoryCode,
        String categoryLabel
) {}
