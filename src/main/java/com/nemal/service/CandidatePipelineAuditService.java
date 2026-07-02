package com.nemal.service;

import com.nemal.dto.CandidatePipelineStatusEventDto;
import com.nemal.entity.Candidate;
import com.nemal.entity.CandidatePipelineStatusEvent;
import com.nemal.entity.Designation;
import com.nemal.entity.User;
import com.nemal.enums.MasterStatus;
import com.nemal.enums.PipelineAuditActionType;
import com.nemal.repository.CandidatePipelineStatusEventRepository;
import com.nemal.repository.CandidateRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class CandidatePipelineAuditService {

    private final CandidatePipelineStatusEventRepository eventRepository;
    private final CandidateRepository candidateRepository;

    public CandidatePipelineAuditService(CandidatePipelineStatusEventRepository eventRepository,
                                         CandidateRepository candidateRepository) {
        this.eventRepository = eventRepository;
        this.candidateRepository = candidateRepository;
    }

    @Transactional
    public void recordStatusChange(Long candidateId,
                                   MasterStatus newStatus,
                                   MasterStatus previousStatus,
                                   PipelineAuditActionType actionType,
                                   User changedBy,
                                   String notes) {
        if (candidateId == null || newStatus == null || actionType == null) {
            return;
        }

        Candidate candidate = candidateRepository.findById(candidateId)
                .orElseThrow(() -> new RuntimeException("Candidate not found: " + candidateId));

        eventRepository.save(CandidatePipelineStatusEvent.builder()
                .candidate(candidate)
                .statusKey(newStatus.name())
                .previousStatusKey(previousStatus != null ? previousStatus.name() : null)
                .actionType(actionType)
                .changedBy(changedBy)
                .changedByName(resolveActorName(changedBy))
                .changedByDesignation(resolveActorDesignation(changedBy))
                .notes(notes)
                .build());
    }

    @Transactional(readOnly = true)
    public List<CandidatePipelineStatusEventDto> getEventsForCandidate(Long candidateId) {
        return eventRepository.findByCandidate_IdOrderByCreatedAtDesc(candidateId)
                .stream()
                .map(CandidatePipelineStatusEventDto::from)
                .toList();
    }

    private String resolveActorName(User user) {
        if (user == null) {
            return "System";
        }
        String fullName = user.getFullName();
        if (fullName != null && !fullName.isBlank()) {
            return fullName.trim();
        }
        return user.getEmail() != null ? user.getEmail() : "Unknown user";
    }

    private String resolveActorDesignation(User user) {
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
