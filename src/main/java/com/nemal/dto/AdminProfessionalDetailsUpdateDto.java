package com.nemal.dto;

import java.util.List;

public record AdminProfessionalDetailsUpdateDto(
        Long departmentId,
        Long designationId,
        Integer yearsOfExperience,
        List<Long> domainIds
) {}
