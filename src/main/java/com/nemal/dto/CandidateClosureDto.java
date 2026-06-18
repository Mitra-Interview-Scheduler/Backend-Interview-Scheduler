package com.nemal.dto;

import com.nemal.entity.CandidateClosure;
import com.nemal.enums.MasterStatus;

import java.time.LocalDateTime;

public record CandidateClosureDto(
        Long id,
        Long closingReasonId,
        String closingReasonCode,
        String closingReasonLabel,
        MasterStatus closedStatus,
        String closedStatusLabel,
        String comment,
        String closedByName,
        LocalDateTime closedAt
) {
    public static CandidateClosureDto from(CandidateClosure closure) {
        ClosingReasonDto reason = closure.getClosingReason() != null
                ? ClosingReasonDto.from(closure.getClosingReason())
                : null;

        MasterStatus closedStatus = null;
        try {
            closedStatus = MasterStatus.valueOf(closure.getClosedStatusKey());
        } catch (IllegalArgumentException ignored) {
            // keep null for unknown keys such as REOPEN
        }

        String closedByName = closure.getClosedBy() != null
                ? closure.getClosedBy().getFullName().trim()
                : null;

        return new CandidateClosureDto(
                closure.getId(),
                reason != null ? reason.id() : null,
                reason != null ? reason.code() : null,
                reason != null ? reason.label() : null,
                closedStatus,
                closedStatus != null ? closedStatus.name().replace('_', ' ') : closure.getClosedStatusKey(),
                closure.getComment(),
                closedByName,
                closure.getClosedAt()
        );
    }
}
