package com.nemal.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.nemal.entity.CandidateTechnology;

public record CandidateTechnologyDto(
        Long id,
        TechnologyDto technology,
        @JsonProperty("isActive") boolean isActive,
        @JsonProperty("isCore") boolean isCore
) {
    public static CandidateTechnologyDto from(CandidateTechnology ct) {
        return new CandidateTechnologyDto(
                ct.getId(),
                TechnologyDto.from(ct.getTechnology()),
                ct.isActive(),
                ct.isCore()
        );
    }
}
