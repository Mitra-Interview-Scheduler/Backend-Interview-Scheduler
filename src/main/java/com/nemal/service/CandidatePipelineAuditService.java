package com.nemal.service;

import com.nemal.dto.CandidatePipelineStatusEventDto;
import com.nemal.entity.Candidate;
import com.nemal.entity.CandidatePipelineStatusEvent;
import com.nemal.entity.MasterStep;
import com.nemal.entity.User;
import com.nemal.enums.MasterStatus;
import com.nemal.enums.PipelineAuditActionType;
import com.nemal.repository.CandidatePipelineStatusEventRepository;
import com.nemal.repository.CandidateRepository;
import com.nemal.repository.MasterStepRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class CandidatePipelineAuditService {

    private static final Logger logger = LoggerFactory.getLogger(CandidatePipelineAuditService.class);

    private final CandidatePipelineStatusEventRepository eventRepository;
    private final CandidateRepository candidateRepository;
    private final MasterStepRepository masterStepRepository;

    public CandidatePipelineAuditService(CandidatePipelineStatusEventRepository eventRepository,
                                         CandidateRepository candidateRepository,
                                         MasterStepRepository masterStepRepository) {
        this.eventRepository = eventRepository;
        this.candidateRepository = candidateRepository;
        this.masterStepRepository = masterStepRepository;
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

        MasterStep masterStep = masterStepRepository.findByStatusKey(newStatus.name());
        if (masterStep == null) {
            logger.warn("No master step configured for status key '{}' on candidate {}", newStatus.name(), candidateId);
            return;
        }

        MasterStep previousMasterStep = null;
        if (previousStatus != null) {
            previousMasterStep = masterStepRepository.findByStatusKey(previousStatus.name());
            if (previousMasterStep == null) {
                logger.warn("No master step configured for previous status key '{}' on candidate {}",
                        previousStatus.name(), candidateId);
            }
        }

        Candidate candidate = candidateRepository.findById(candidateId)
                .orElseThrow(() -> new RuntimeException("Candidate not found: " + candidateId));

        eventRepository.save(CandidatePipelineStatusEvent.builder()
                .candidate(candidate)
                .masterStep(masterStep)
                .previousMasterStep(previousMasterStep)
                .actionType(actionType)
                .changedBy(changedBy)
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
}
