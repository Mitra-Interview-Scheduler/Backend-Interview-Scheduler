package com.nemal.dto;

public record CreateTechnologyDto(
        String name,
        String code,
        Long categoryId
) {}
