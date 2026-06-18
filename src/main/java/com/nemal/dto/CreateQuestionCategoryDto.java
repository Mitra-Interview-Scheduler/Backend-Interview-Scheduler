package com.nemal.dto;

public record CreateQuestionCategoryDto(
        String code,
        String label,
        Integer displayOrder
) {}
