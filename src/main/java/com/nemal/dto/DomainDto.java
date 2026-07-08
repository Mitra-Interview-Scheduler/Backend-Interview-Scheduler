package com.nemal.dto;

import com.nemal.entity.Domain;

public record DomainDto(
        Long id,
        String code,
        String name,
        boolean isActive
) {
    public static DomainDto from(Domain domain) {
        return new DomainDto(
                domain.getId(),
                domain.getCode(),
                domain.getName(),
                domain.isActive()
        );
    }
}
