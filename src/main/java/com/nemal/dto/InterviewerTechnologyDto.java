package com.nemal.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.nemal.entity.InterviewerTechnology;

public record InterviewerTechnologyDto(
        Long id,
        TechnologyDto technology,
        int yearsOfExperience,
        @JsonProperty("isActive") boolean isActive,
        @JsonProperty("isCore") boolean isCore
) {
    public static InterviewerTechnologyDto from(InterviewerTechnology it) {
        return new InterviewerTechnologyDto(
                it.getId(),
                TechnologyDto.from(it.getTechnology()),
                it.getYearsOfExperience(),
                it.isActive(),
                it.isCore()
        );
    }
}