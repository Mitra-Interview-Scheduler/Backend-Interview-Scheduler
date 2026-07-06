package com.nemal.dto;

public record UpdateDomainDto(
        String name,
        String code,
        Boolean isActive
) {}
