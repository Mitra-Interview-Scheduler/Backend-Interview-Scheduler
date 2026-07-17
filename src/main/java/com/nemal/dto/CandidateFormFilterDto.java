package com.nemal.dto;

public record CandidateFormFilterDto(
        Long targetDesignationId,
        Long departmentId,
        String interviewType
) {}
