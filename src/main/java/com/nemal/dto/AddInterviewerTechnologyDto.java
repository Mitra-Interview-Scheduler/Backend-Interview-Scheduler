package com.nemal.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public record AddInterviewerTechnologyDto(
        Long technologyId,
        int yearsOfExperience,
        @JsonProperty("isCore") Boolean isCore
) {}