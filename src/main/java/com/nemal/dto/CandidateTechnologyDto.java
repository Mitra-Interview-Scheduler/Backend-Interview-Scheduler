package com.nemal.dto;

import com.nemal.entity.CandidateTechnology;

public record CandidateTechnologyDto(
        Long id,
        TechnologyDto technology,
        boolean isActive
) {
    public static CandidateTechnologyDto from(CandidateTechnology ct) {
        return new CandidateTechnologyDto(
                ct.getId(),
                TechnologyDto.from(ct.getTechnology()),
                ct.isActive()
        );
    }
}
