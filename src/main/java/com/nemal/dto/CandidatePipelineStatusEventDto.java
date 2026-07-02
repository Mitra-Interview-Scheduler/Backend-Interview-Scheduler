package com.nemal.dto;

import com.nemal.entity.CandidatePipelineStatusEvent;
import com.nemal.entity.Designation;
import com.nemal.entity.User;
import com.nemal.enums.PipelineAuditActionType;

import java.time.LocalDateTime;

public record CandidatePipelineStatusEventDto(
        Long id,
        Long candidateId,
        Long masterStepId,
        Long previousMasterStepId,
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
                event.getMasterStep() != null ? event.getMasterStep().getId() : null,
                event.getPreviousMasterStep() != null ? event.getPreviousMasterStep().getId() : null,
                event.getMasterStep() != null ? event.getMasterStep().getStatusKey() : null,
                event.getPreviousMasterStep() != null ? event.getPreviousMasterStep().getStatusKey() : null,
                event.getActionType(),
                event.getChangedBy() != null ? event.getChangedBy().getId() : null,
                resolveActorName(event.getChangedBy()),
                resolveActorDesignation(event.getChangedBy()),
                event.getNotes(),
                event.getCreatedAt()
        );
    }

    private static String resolveActorName(User user) {
        if (user == null) {
            return "System";
        }
        String fullName = user.getFullName();
        if (fullName != null && !fullName.isBlank()) {
            return fullName.trim();
        }
        return user.getEmail() != null ? user.getEmail() : "Unknown user";
    }

    private static String resolveActorDesignation(User user) {
        if (user == null) {
            return null;
        }
        Designation designation = user.getCurrentDesignation();
        if (designation == null || designation.getName() == null || designation.getName().isBlank()) {
            return null;
        }
        return designation.getName().trim();
    }
}
