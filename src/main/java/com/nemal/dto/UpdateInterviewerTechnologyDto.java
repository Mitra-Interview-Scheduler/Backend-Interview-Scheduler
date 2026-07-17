package com.nemal.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public record UpdateInterviewerTechnologyDto(
        @JsonProperty("isCore") Boolean isCore
) {}
