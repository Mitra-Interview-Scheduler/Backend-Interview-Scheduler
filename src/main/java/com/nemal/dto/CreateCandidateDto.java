package com.nemal.dto;

public record CreateCandidateDto(
        String name,
        String email,
        String phone,
        Long departmentId,
        Long targetDesignationId,
        String resumeUrl,
        String jdUrl,
        String jobReferenceCode,
        String resourceRequestNumber,
        String location,
        String notes,
        Integer yearsOfExperience
) {}