package com.nemal.dto;

import com.nemal.entity.DocumentType;
import com.nemal.entity.ResourceType;

public record CatalogTypeDto(
        Long id,
        String code,
        String label,
        int displayOrder,
        boolean active
) {
    public static CatalogTypeDto from(DocumentType type) {
        return new CatalogTypeDto(
                type.getId(),
                type.getCode(),
                type.getLabel(),
                type.getDisplayOrder(),
                type.isActive()
        );
    }

    public static CatalogTypeDto from(ResourceType type) {
        return new CatalogTypeDto(
                type.getId(),
                type.getCode(),
                type.getLabel(),
                type.getDisplayOrder(),
                type.isActive()
        );
    }
}
