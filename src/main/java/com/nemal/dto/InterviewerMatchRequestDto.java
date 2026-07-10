package com.nemal.dto;

import java.util.List;

public record InterviewerMatchRequestDto(
        Long candidateId,
        List<Long> departmentIds,
        Integer minYearsOfExperience,
        Long minTierId,
        Long minDesignationLevelInDepartment,
        Long departmentIdForDesignationFilter,
        Integer limit
) {}
