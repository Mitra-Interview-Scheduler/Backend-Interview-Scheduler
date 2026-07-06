package com.nemal.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public record UpdateCandidateTechnologyDto(
        @JsonProperty("isCore") Boolean isCore
) {}
