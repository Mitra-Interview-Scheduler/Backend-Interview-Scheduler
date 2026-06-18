package com.nemal.dto;

public record UpdateTechnologyCategoryDto(
        String code,
        String label,
        Integer displayOrder,
        Boolean isActive
) {}
