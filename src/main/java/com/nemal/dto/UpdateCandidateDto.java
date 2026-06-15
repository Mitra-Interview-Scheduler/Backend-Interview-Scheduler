package com.nemal.dto;

import com.nemal.enums.MasterStatus;

public record UpdateCandidateDto(
        String name,
        String email,
        String phone,
        Long departmentId,
        Long targetDesignationId,
        MasterStatus status,
        String resumeUrl,
        String jdUrl,
        String resourceLink,
        String jobReferenceCode,
        String resourceRequestNumber,
        String location,
        String notes,
        Integer yearsOfExperience,
        Boolean isActive,
        Boolean addPipelineRound
) {}