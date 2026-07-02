package com.nemal.dto;

public record AdminProfessionalDetailsUpdateDto(
        Long departmentId,
        Long designationId,
        Integer yearsOfExperience
) {}
