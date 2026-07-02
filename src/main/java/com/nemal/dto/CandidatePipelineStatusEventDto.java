package com.nemal.dto;

import com.nemal.entity.CandidatePipelineStatusEvent;
import com.nemal.enums.PipelineAuditActionType;

import java.time.LocalDateTime;

public record CandidatePipelineStatusEventDto(
        Long id,
        Long candidateId,
        String statusKey,
        String previousStatusKey,
        PipelineAuditActionType actionType,
        Long changedByUserId,
        String changedByName,
        String changedByDesignation,
        String notes,
        LocalDateTime createdAt
) {
    public static CandidatePipelineStatusEventDto from(CandidatePipelineStatusEvent event) {
        return new CandidatePipelineStatusEventDto(
                event.getId(),
                event.getCandidate() != null ? event.getCandidate().getId() : null,
                event.getStatusKey(),
                event.getPreviousStatusKey(),
                event.getActionType(),
                event.getChangedBy() != null ? event.getChangedBy().getId() : null,
                event.getChangedByName(),
                event.getChangedByDesignation(),
                event.getNotes(),
                event.getCreatedAt()
        );
    }
}
