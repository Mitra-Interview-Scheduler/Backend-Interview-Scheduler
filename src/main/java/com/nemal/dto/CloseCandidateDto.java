package com.nemal.dto;

import com.nemal.enums.MasterStatus;

public record CloseCandidateDto(
        MasterStatus status,
        Long closingReasonId,
        String comment
) {}
