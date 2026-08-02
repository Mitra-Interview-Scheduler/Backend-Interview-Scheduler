package com.nemal.dto;

public record UpdateCatalogTypeDto(
        String code,
        String label,
        Integer displayOrder,
        Boolean active
) {}
