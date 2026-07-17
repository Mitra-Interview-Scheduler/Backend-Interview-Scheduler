// AvailabilityFilterDto.java
package com.nemal.dto;

import java.time.LocalDateTime;
import java.util.List;

public record AvailabilityFilterDto(
        List<Long> departmentIds,
        List<Long> technologyIds,
        List<Long> domainIds,
        LocalDateTime startDateTime,
        LocalDateTime endDateTime,
        Integer minYearsOfExperience,
        Long minDesignationLevelInDepartment,
        Long departmentIdForDesignationFilter,
        Long minTierId,
        Integer page,
        Integer size
) {}