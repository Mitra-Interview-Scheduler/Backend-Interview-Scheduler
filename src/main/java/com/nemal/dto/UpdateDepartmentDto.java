package com.nemal.dto;

public record UpdateDepartmentDto(
        String name,
        String code,
        Boolean isActive
) {}
