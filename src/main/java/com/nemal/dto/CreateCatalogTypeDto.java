package com.nemal.dto;

public record CreateCatalogTypeDto(
        String code,
        String label,
        Integer displayOrder
) {}
