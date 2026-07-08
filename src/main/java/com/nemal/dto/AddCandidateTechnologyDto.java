package com.nemal.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public record AddCandidateTechnologyDto(
        Long technologyId,
        @JsonProperty("isCore") Boolean isCore
) {}
