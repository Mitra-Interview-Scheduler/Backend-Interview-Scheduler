package com.nemal.dto;

import com.nemal.enums.CandidateStatus;

public record UpdateCandidateDto(
        String name,
        String email,
        String phone,
        Long departmentId,
        Long targetDesignationId,
        CandidateStatus status,
        String resumeUrl,
        String jdUrl,
        String jobReferenceCode,
        String resourceRequestNumber,
        String location,
        String notes,
        Integer yearsOfExperience,
        Boolean isActive
) {}