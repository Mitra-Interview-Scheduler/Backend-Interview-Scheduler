package com.nemal.service;

import com.nemal.dto.CandidateClosureDto;
import com.nemal.dto.CandidateDto;
import com.nemal.dto.CloseCandidateDto;
import com.nemal.entity.Candidate;
import com.nemal.entity.CandidateClosure;
import com.nemal.entity.ClosingReason;
import com.nemal.entity.MasterStep;
import com.nemal.entity.User;
import com.nemal.enums.MasterStatus;
import com.nemal.repository.CandidateClosureRepository;
import com.nemal.repository.CandidateRepository;
import com.nemal.repository.ClosingReasonRepository;
import com.nemal.repository.MasterStepRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
public class CandidateClosureService {
    private final CandidateRepository candidateRepository;
    private final ClosingReasonRepository closingReasonRepository;
    private final CandidateClosureRepository candidateClosureRepository;
    private final MasterStepRepository masterStepRepository;
    private final MasterStepService masterStepService;
    private final CandidateStepPipelineService candidateStepPipelineService;

    public CandidateClosureService(
            CandidateRepository candidateRepository,
            ClosingReasonRepository closingReasonRepository,
            CandidateClosureRepository candidateClosureRepository,
            MasterStepRepository masterStepRepository,
            MasterStepService masterStepService,
            CandidateStepPipelineService candidateStepPipelineService
    ) {
        this.candidateRepository = candidateRepository;
        this.closingReasonRepository = closingReasonRepository;
        this.candidateClosureRepository = candidateClosureRepository;
        this.masterStepRepository = masterStepRepository;
        this.masterStepService = masterStepService;
        this.candidateStepPipelineService = candidateStepPipelineService;
    }

    @Transactional(readOnly = true)
    public CandidateClosureDto getLatestClosure(Long candidateId) {
        return candidateClosureRepository.findTopByCandidateIdOrderByClosedAtDesc(candidateId)
                .map(CandidateClosureDto::from)
                .orElse(null);
    }

    @Transactional
    public CandidateDto closeCandidate(Long candidateId, CloseCandidateDto dto, User closedBy) {
        boolean isSelected = dto.status() == MasterStatus.SELECTED;
        String comment = dto.comment() != null ? dto.comment().trim() : "";

        if (!isSelected) {
            if (dto.closingReasonId() == null) {
                throw new IllegalArgumentException("Closing reason is required.");
            }
            if (comment.isEmpty()) {
                throw new IllegalArgumentException("A reason or comment is required when closing an application.");
            }
        }

        Candidate candidate = candidateRepository.findByIdAndIsActiveTrue(candidateId)
                .orElseThrow(() -> new RuntimeException("Candidate not found"));

        MasterStep targetStep = masterStepRepository.findByStatusKey(dto.status().name());
        if (targetStep == null || !targetStep.isClosingStep() || !targetStep.isVisible()) {
            throw new IllegalArgumentException("Status must be a visible closing stage.");
        }

        ClosingReason closingReason = null;
        if (dto.closingReasonId() != null) {
            closingReason = closingReasonRepository.findById(dto.closingReasonId())
                    .filter(ClosingReason::isActive)
                    .orElseThrow(() -> new IllegalArgumentException("Closing reason not found."));
        }

        MasterStatus previousStatus = candidate.getStatus();
        masterStepService.assignStatus(candidate, dto.status());
        candidateRepository.save(candidate);

        candidateStepPipelineService.updatePipelineOnStatusChange(
                candidateId,
                dto.status(),
                previousStatus,
                false
        );

        CandidateClosure closure = CandidateClosure.builder()
                .candidate(candidate)
                .closingReason(closingReason)
                .closedStatusKey(dto.status().name())
                .comment(comment.isEmpty() ? null : comment)
                .closedBy(closedBy)
                .closedAt(LocalDateTime.now())
                .build();
        candidateClosureRepository.save(closure);

        return CandidateDto.from(candidate, CandidateClosureDto.from(closure));
    }
}
