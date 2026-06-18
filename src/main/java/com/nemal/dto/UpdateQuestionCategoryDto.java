package com.nemal.dto;

public record UpdateQuestionCategoryDto(
        String code,
        String label,
        Integer displayOrder,
        Boolean isActive
) {}
