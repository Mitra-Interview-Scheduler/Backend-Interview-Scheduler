package com.nemal.dto;

public record CreateTechnologyCategoryDto(
        String code,
        String label,
        Integer displayOrder
) {}
