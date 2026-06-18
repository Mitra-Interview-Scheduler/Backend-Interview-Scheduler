package com.nemal.dto;

public record UpdateTechnologyDto(
        String name,
        String code,
        Long categoryId,
        Boolean isActive
) {}
