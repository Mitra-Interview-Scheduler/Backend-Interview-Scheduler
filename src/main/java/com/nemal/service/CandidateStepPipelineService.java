package com.nemal.service;

import com.nemal.entity.Candidate;
import com.nemal.entity.CandidateStepPipeline;
import com.nemal.entity.MasterStep;
import com.nemal.enums.MasterStatus;
import com.nemal.enums.PipelineStepStatus;
import com.nemal.repository.CandidateRepository;
import com.nemal.repository.CandidateStepPipelineRepository;
import com.nemal.repository.MasterStepRepository;
import jakarta.transaction.Transactional;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

@Service
public class CandidateStepPipelineService {

    private static final Set<MasterStatus> REPEATABLE_STATUSES = EnumSet.of(
            MasterStatus.TECHNICAL_ROUND,
            MasterStatus.HR_ROUND
    );

    private final CandidateStepPipelineRepository pipelineRepository;
    private final CandidateRepository candidateRepository;
    private final MasterStepRepository masterStepRepository;
    private final MasterStepService masterStepService;

    public CandidateStepPipelineService(CandidateStepPipelineRepository pipelineRepository,
                                        CandidateRepository candidateRepository,
                                        MasterStepRepository masterStepRepository,
                                        MasterStepService masterStepService) {
        this.pipelineRepository = pipelineRepository;
        this.candidateRepository = candidateRepository;
        this.masterStepRepository = masterStepRepository;
        this.masterStepService = masterStepService;
    }

    @Transactional
    public List<CandidateStepPipeline> getPipelineForCandidate(Long candidateId) {
        return pipelineRepository.findByCandidateIdOrderBySequenceOrderAsc(candidateId);
    }

    /**
     * Initializes the default pipeline. Each row's sequence_order matches the master step's step_order.
     */
    @Transactional
    public void initializeDefaultPipeline(Long candidateId) {
        Candidate candidate = candidateRepository.findById(candidateId)
                .orElseThrow(() -> new RuntimeException("Candidate record profile not found"));

        if (!pipelineRepository.findByCandidateIdOrderBySequenceOrderAsc(candidateId).isEmpty()) {
            return;
        }

        List<MasterStep> masterSteps = masterStepRepository
                .findByIsDefaultStepTrueOrderByStepOrderAscDisplayOrderAsc();
        if (masterSteps.isEmpty()) {
            throw new RuntimeException("Master step type not configured");
        }

        for (MasterStep masterStep : masterSteps) {
            CandidateStepPipeline stepInstance = CandidateStepPipeline.builder()
                    .candidate(candidate)
                    .step(masterStep)
                    .sequenceOrder(masterStep.getStepOrder())
                    .stepStatus(masterStep.getStepOrder() == 1
                            ? PipelineStepStatus.CURRENT
                            : PipelineStepStatus.PENDING)
                    .build();

            pipelineRepository.save(stepInstance);
        }
    }

    @Transactional
    public void handleStepFailure(Long candidateId, Integer sequenceOrder) {
        pipelineRepository.updateStepStatus(candidateId, sequenceOrder, PipelineStepStatus.FAILED);
        pipelineRepository.skipUpcomingSteps(candidateId, sequenceOrder);

        Candidate candidate = candidateRepository.findById(candidateId)
                .orElseThrow(() -> new RuntimeException("Candidate record profile not found"));
        masterStepService.assignStatus(candidate, MasterStatus.REJECTED);
        candidateRepository.save(candidate);
    }

    @Transactional
    public void completeStepAndAdvance(Long candidateId, Integer currentSequenceOrder) {
        pipelineRepository.updateStepStatus(candidateId, currentSequenceOrder, PipelineStepStatus.COMPLETED);
        pipelineRepository.updateStepStatus(candidateId, currentSequenceOrder + 1, PipelineStepStatus.CURRENT);
    }

    /**
     * Inserts a master step immediately after {@code insertAfterSequenceOrder}.
     * Steps at or after the new position are pushed back by one; the new step becomes CURRENT.
     */
    @Transactional
    public void insertStepAfter(Long candidateId, String statusKey, int insertAfterSequenceOrder) {
        Candidate candidate = candidateRepository.findById(candidateId)
                .orElseThrow(() -> new RuntimeException("Candidate record profile not found"));

        MasterStep masterStep = masterStepRepository.findByStatusKey(statusKey);
        if (masterStep == null) {
            throw new RuntimeException("Master step type configuration not found for: " + statusKey);
        }

        int targetSequenceOrder = insertAfterSequenceOrder + 1;

        // Complete the step we are inserting after
        pipelineRepository.updateStepStatus(candidateId, insertAfterSequenceOrder, PipelineStepStatus.COMPLETED);

        // Push back every step at or after the insertion slot
        pipelineRepository.shiftSequenceOrdersUp(candidateId, targetSequenceOrder);

        CandidateStepPipeline newStep = CandidateStepPipeline.builder()
                .candidate(candidate)
                .step(masterStep)
                .sequenceOrder(targetSequenceOrder)
                .stepStatus(PipelineStepStatus.CURRENT)
                .build();

        pipelineRepository.save(newStep);

        // Downstream steps stay pending after the shift
        pipelineRepository.updateStepStatusAfter(candidateId, targetSequenceOrder, PipelineStepStatus.PENDING);
        pipelineRepository.updateStepStatus(candidateId, targetSequenceOrder, PipelineStepStatus.CURRENT);
    }

    /**
     * Activates an existing pipeline row: prior steps completed, target current, later steps pending.
     */
    @Transactional
    public void activateExistingStep(Long candidateId, int targetSequenceOrder) {
        pipelineRepository.updateStepStatusBefore(candidateId, targetSequenceOrder, PipelineStepStatus.COMPLETED);
        pipelineRepository.updateStepStatusAfter(candidateId, targetSequenceOrder, PipelineStepStatus.PENDING);
        pipelineRepository.updateStepStatus(candidateId, targetSequenceOrder, PipelineStepStatus.CURRENT);
    }

    /**
     * When candidate macro-status changes, sync the pipeline:
     * - technical / HR rounds always append after the current step (never reactivate an older round)
     * - other statuses dedupe: activate the existing row or insert once
     */
    @Transactional
    public void updatePipelineOnStatusChange(Long candidateId,
                                             MasterStatus newStatus,
                                             MasterStatus previousStatus,
                                             boolean addPipelineRound) {
        List<CandidateStepPipeline> pipeline = pipelineRepository.findByCandidateIdOrderBySequenceOrderAsc(candidateId);
        if (pipeline.isEmpty()) {
            return;
        }

        if (REPEATABLE_STATUSES.contains(newStatus)) {
            handleRepeatableStatusChange(candidateId, newStatus, pipeline);
            return;
        }

        Optional<CandidateStepPipeline> existingStep = pipeline.stream()
                .filter(p -> p.getStep() != null
                        && p.getStep().getStatusKey().equalsIgnoreCase(newStatus.name()))
                .findFirst();

        if (existingStep.isPresent()) {
            activateExistingStep(candidateId, existingStep.get().getSequenceOrder());
            return;
        }

        MasterStep masterStep = masterStepRepository.findByStatusKey(newStatus.name());
        if (masterStep == null) {
            if (newStatus == MasterStatus.REJECTED) {
                pipeline.stream()
                        .filter(p -> p.getStepStatus() == PipelineStepStatus.CURRENT)
                        .findFirst()
                        .ifPresent(current -> handleStepFailure(candidateId, current.getSequenceOrder()));
            }
            return;
        }

        int insertAfterSequenceOrder = resolveInsertAfterSequenceOrder(pipeline);
        insertStepAfter(candidateId, newStatus.name(), insertAfterSequenceOrder);
    }

    private void handleRepeatableStatusChange(Long candidateId,
                                              MasterStatus newStatus,
                                              List<CandidateStepPipeline> pipeline) {
        MasterStep masterStep = masterStepRepository.findByStatusKey(newStatus.name());
        if (masterStep == null) {
            return;
        }

        int insertAfterSequenceOrder = resolveInsertAfterSequenceOrder(pipeline);
        insertStepAfter(candidateId, newStatus.name(), insertAfterSequenceOrder);
    }

    private int resolveInsertAfterSequenceOrder(List<CandidateStepPipeline> pipeline) {
        Optional<CandidateStepPipeline> currentActive = pipeline.stream()
                .filter(p -> p.getStepStatus() == PipelineStepStatus.CURRENT)
                .findFirst();

        if (currentActive.isPresent()) {
            return currentActive.get().getSequenceOrder();
        }

        return pipeline.stream()
                .map(CandidateStepPipeline::getSequenceOrder)
                .max(Comparator.naturalOrder())
                .orElse(0);
    }
}
