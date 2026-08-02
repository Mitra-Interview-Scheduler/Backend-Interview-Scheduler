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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

@Service
public class CandidateStepPipelineService {

    private static final Logger logger = LoggerFactory.getLogger(CandidateStepPipelineService.class);

    private static final Set<MasterStatus> APPENDABLE_STATUSES = EnumSet.of(
            MasterStatus.TECHNICAL_ROUND,
            MasterStatus.HR_ROUND,
            MasterStatus.ON_HOLD,
            MasterStatus.OFFER_PENDING
    );

    private final CandidateStepPipelineRepository pipelineRepository;
    private final CandidateRepository candidateRepository;
    private final MasterStepRepository masterStepRepository;
    private final MasterStepService masterStepService;
    private final InterviewTypeService interviewTypeService;

    public CandidateStepPipelineService(CandidateStepPipelineRepository pipelineRepository,
                                        CandidateRepository candidateRepository,
                                        MasterStepRepository masterStepRepository,
                                        MasterStepService masterStepService,
                                        InterviewTypeService interviewTypeService) {
        this.pipelineRepository = pipelineRepository;
        this.candidateRepository = candidateRepository;
        this.masterStepRepository = masterStepRepository;
        this.masterStepService = masterStepService;
        this.interviewTypeService = interviewTypeService;
    }

    @Transactional
    public List<CandidateStepPipeline> getPipelineForCandidate(Long candidateId) {
        return pipelineRepository.findByCandidateIdOrderBySequenceOrderAsc(candidateId);
    }

    /**
     * Initializes the default pipeline from all active master steps flagged as default.
     * Uses a sequential sequence_order (1..n) so multiple master steps that share the
     * same step_order value do not violate uk_candidate_sequence_order.
     */
    @Transactional
    public void initializeDefaultPipeline(Long candidateId) {
        Candidate candidate = candidateRepository.findById(candidateId)
                .orElseThrow(() -> new RuntimeException("Candidate record profile not found"));

        if (!pipelineRepository.findByCandidateIdOrderBySequenceOrderAsc(candidateId).isEmpty()) {
            return;
        }

        List<MasterStep> masterSteps = masterStepRepository
                .findByIsDefaultStepTrueAndIsActiveTrueOrderByStepOrderAscDisplayOrderAsc();
        if (masterSteps.isEmpty()) {
            throw new RuntimeException("Master step type not configured");
        }

        int sequenceOrder = 1;
        for (MasterStep masterStep : masterSteps) {
            CandidateStepPipeline stepInstance = CandidateStepPipeline.builder()
                    .candidate(candidate)
                    .step(masterStep)
                    .sequenceOrder(sequenceOrder)
                    .stepStatus(sequenceOrder == 1
                            ? PipelineStepStatus.CURRENT
                            : PipelineStepStatus.PENDING)
                    .build();

            pipelineRepository.save(stepInstance);
            sequenceOrder++;
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
     * Marks the active interview-round pipeline row as completed when its interview finishes.
     */
    @Transactional
    public void completeInterviewRoundStep(Long candidateId, String interviewTypeCode) {
        if (candidateId == null || interviewTypeCode == null || interviewTypeCode.isBlank()) {
            return;
        }

        List<CandidateStepPipeline> pipeline = pipelineRepository.findByCandidateIdOrderBySequenceOrderAsc(candidateId);
        if (pipeline.isEmpty()) {
            return;
        }

        String roundKey = interviewTypeService.roundStatusKey(interviewTypeCode);

        Optional<CandidateStepPipeline> roundStep = pipeline.stream()
                .filter(step -> step.getStep() != null
                        && roundKey.equalsIgnoreCase(step.getStep().getStatusKey()))
                .filter(step -> step.getStepStatus() == PipelineStepStatus.CURRENT)
                .max(Comparator.comparing(CandidateStepPipeline::getSequenceOrder));

        if (roundStep.isEmpty()) {
            roundStep = pipeline.stream()
                    .filter(step -> step.getStep() != null
                            && roundKey.equalsIgnoreCase(step.getStep().getStatusKey()))
                    .filter(step -> step.getStepStatus() == PipelineStepStatus.PENDING)
                    .max(Comparator.comparing(CandidateStepPipeline::getSequenceOrder));
        }

        roundStep.ifPresent(step -> pipelineRepository.updateStepStatus(
                candidateId, step.getSequenceOrder(), PipelineStepStatus.COMPLETED));
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

        List<CandidateStepPipeline> pipeline = pipelineRepository.findByCandidateIdOrderBySequenceOrderAsc(candidateId);
        boolean shouldCompletePrior = pipeline.stream()
                .anyMatch(step -> step.getSequenceOrder() == insertAfterSequenceOrder
                        && step.getStepStatus() == PipelineStepStatus.CURRENT);

        if (shouldCompletePrior) {
            pipelineRepository.updateStepStatus(candidateId, insertAfterSequenceOrder, PipelineStepStatus.COMPLETED);
        }

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
     * Closes the pipeline when a candidate reaches a closing stage (Rejected, Selected, etc.).
     * Fails or completes the current step, skips pending steps, then activates or appends the closing step.
     */
    @Transactional
    public void closePipeline(Long candidateId, MasterStatus closingStatus) {
        if (closingStatus == null) {
            return;
        }

        MasterStep closingMasterStep = masterStepRepository.findByStatusKey(closingStatus.name());
        if (closingMasterStep == null || !closingMasterStep.isClosingStep()) {
            throw new IllegalArgumentException("Invalid closing status: " + closingStatus);
        }

        List<CandidateStepPipeline> pipeline = pipelineRepository.findByCandidateIdOrderBySequenceOrderAsc(candidateId);
        if (pipeline.isEmpty()) {
            return;
        }

        Optional<CandidateStepPipeline> currentStep = pipeline.stream()
                .filter(p -> p.getStepStatus() == PipelineStepStatus.CURRENT)
                .findFirst();

        if (currentStep.isPresent()) {
            int sequenceOrder = currentStep.get().getSequenceOrder();
            PipelineStepStatus terminalStatus = closingStatus == MasterStatus.REJECTED
                    ? PipelineStepStatus.FAILED
                    : PipelineStepStatus.COMPLETED;
            pipelineRepository.updateStepStatus(candidateId, sequenceOrder, terminalStatus);
            pipelineRepository.skipUpcomingSteps(candidateId, sequenceOrder);
        } else {
            pipeline.stream()
                    .filter(p -> p.getStepStatus() == PipelineStepStatus.PENDING)
                    .forEach(p -> pipelineRepository.updateStepStatus(
                            candidateId, p.getSequenceOrder(), PipelineStepStatus.SKIPPED));
        }

        pipeline = pipelineRepository.findByCandidateIdOrderBySequenceOrderAsc(candidateId);

        Optional<CandidateStepPipeline> existingClosingStep = pipeline.stream()
                .filter(p -> p.getStep() != null
                        && p.getStep().getStatusKey().equalsIgnoreCase(closingStatus.name()))
                .findFirst();

        if (existingClosingStep.isPresent()) {
            activateExistingStep(candidateId, existingClosingStep.get().getSequenceOrder());
            return;
        }

        int insertAfterSequenceOrder = resolveInsertAfterSequenceOrder(pipeline);
        insertStepAfter(candidateId, closingStatus.name(), insertAfterSequenceOrder);
    }

    /**
     * When an interview is cancelled, mark the active round step as failed and reactivate
     * the existing pre-interview stage (e.g. SCREENING) instead of appending a duplicate row.
     */
    @Transactional
    public void restorePipelineAfterInterviewCancel(Long candidateId, String interviewTypeCode) {
        if (candidateId == null || interviewTypeCode == null || interviewTypeCode.isBlank()) {
            return;
        }
        String roundKey = interviewTypeService.roundStatusKey(interviewTypeCode);
        String resetKey = interviewTypeService.cancelRestoreStatusKey(interviewTypeCode);

        List<CandidateStepPipeline> pipeline = pipelineRepository.findByCandidateIdOrderBySequenceOrderAsc(candidateId);
        if (pipeline.isEmpty()) {
            return;
        }

        Optional<CandidateStepPipeline> roundToFail = pipeline.stream()
                .filter(step -> step.getStepStatus() == PipelineStepStatus.CURRENT)
                .filter(step -> step.getStep() != null
                        && roundKey.equalsIgnoreCase(step.getStep().getStatusKey()))
                .max(Comparator.comparing(CandidateStepPipeline::getSequenceOrder));

        if (roundToFail.isEmpty()) {
            roundToFail = pipeline.stream()
                    .filter(step -> step.getStepStatus() == PipelineStepStatus.CURRENT)
                    .max(Comparator.comparing(CandidateStepPipeline::getSequenceOrder));
        }

        roundToFail.ifPresent(step -> pipelineRepository.updateStepStatus(
                candidateId, step.getSequenceOrder(), PipelineStepStatus.FAILED));

        Optional<CandidateStepPipeline> restoreStep = pipeline.stream()
                .filter(step -> step.getStep() != null
                        && resetKey.equalsIgnoreCase(step.getStep().getStatusKey()))
                .max(Comparator.comparing(CandidateStepPipeline::getSequenceOrder));

        if (restoreStep.isEmpty()) {
            logger.warn("No pipeline step found to restore for status {} on candidate {}", resetKey, candidateId);
            return;
        }

        int restoreSequenceOrder = restoreStep.get().getSequenceOrder();

        pipeline.stream()
                .filter(step -> step.getStepStatus() == PipelineStepStatus.CURRENT
                        && step.getSequenceOrder() != restoreSequenceOrder)
                .forEach(step -> pipelineRepository.updateStepStatus(
                        candidateId, step.getSequenceOrder(), PipelineStepStatus.COMPLETED));

        pipelineRepository.updateStepStatusBefore(candidateId, restoreSequenceOrder, PipelineStepStatus.COMPLETED);
        pipelineRepository.updateStepStatus(candidateId, restoreSequenceOrder, PipelineStepStatus.CURRENT);
    }

    /**
     * When candidate macro-status changes, sync the pipeline:
     * - first visit to a pre-seeded PENDING default step activates that row
     * - every re-entry or new round appends after the current step (like interview history)
     */
    @Transactional
    public void updatePipelineOnStatusChange(Long candidateId,
                                             MasterStatus newStatus,
                                             MasterStatus previousStatus,
                                             boolean addPipelineRound) {
        updatePipelineOnStatusChange(
                candidateId,
                newStatus != null ? newStatus.name() : null,
                previousStatus != null ? previousStatus.name() : null,
                addPipelineRound);
    }

    @Transactional
    public void updatePipelineOnStatusChange(Long candidateId,
                                             String newStatusKey,
                                             String previousStatusKey,
                                             boolean addPipelineRound) {
        List<CandidateStepPipeline> pipeline = pipelineRepository.findByCandidateIdOrderBySequenceOrderAsc(candidateId);
        if (pipeline.isEmpty()) {
            return;
        }
        if (newStatusKey == null || newStatusKey.isBlank()) {
            return;
        }

        String normalizedNew = newStatusKey.trim().toUpperCase();
        String normalizedPrevious = previousStatusKey != null && !previousStatusKey.isBlank()
                ? previousStatusKey.trim().toUpperCase()
                : null;

        boolean statusChanged = normalizedPrevious == null || !normalizedPrevious.equalsIgnoreCase(normalizedNew);
        if (!statusChanged && !addPipelineRound) {
            return;
        }

        MasterStep masterStep = masterStepRepository.findByStatusKey(normalizedNew);
        if (masterStep == null) {
            logger.warn("No master step configured for status key '{}' on candidate {}", normalizedNew, candidateId);
            return;
        }

        Optional<CandidateStepPipeline> pendingTemplateStep = pipeline.stream()
                .filter(p -> p.getStep() != null
                        && p.getStep().getStatusKey().equalsIgnoreCase(normalizedNew)
                        && p.getStepStatus() == PipelineStepStatus.PENDING)
                .findFirst();

        MasterStatus newStatusEnum = null;
        try {
            newStatusEnum = MasterStatus.valueOf(normalizedNew);
        } catch (IllegalArgumentException ignored) {
            // Custom interview-round stages are not MasterStatus enum values.
        }

        boolean shouldAppend = addPipelineRound
                || (newStatusEnum != null
                    && APPENDABLE_STATUSES.contains(newStatusEnum)
                    && pendingTemplateStep.isEmpty())
                || (newStatusEnum == null && pendingTemplateStep.isEmpty());

        if (!shouldAppend) {
            Optional<CandidateStepPipeline> stepToActivate = pendingTemplateStep;
            if (stepToActivate.isEmpty()) {
                stepToActivate = pipeline.stream()
                        .filter(p -> p.getStep() != null
                                && p.getStep().getStatusKey().equalsIgnoreCase(normalizedNew))
                        .max(Comparator.comparing(CandidateStepPipeline::getSequenceOrder));
            }
            if (stepToActivate.isPresent()) {
                activateExistingStep(candidateId, stepToActivate.get().getSequenceOrder());
                cleanupDefaultTemplateStepsIfNeeded(candidateId, normalizedNew);
                return;
            }
        }

        if (shouldAppend) {
            boolean appendAfterEngagedHistory = addPipelineRound
                    || MasterStatus.OFFER_PENDING.name().equals(normalizedNew);

            if (appendAfterEngagedHistory) {
                pipeline.stream()
                        .filter(step -> step.getStepStatus() == PipelineStepStatus.CURRENT)
                        .forEach(step -> pipelineRepository.updateStepStatus(
                                candidateId, step.getSequenceOrder(), PipelineStepStatus.COMPLETED));
                pipeline = pipelineRepository.findByCandidateIdOrderBySequenceOrderAsc(candidateId);
            }

            int insertAfterSequenceOrder = appendAfterEngagedHistory
                    ? resolveInsertAfterForNewRound(pipeline)
                    : resolveInsertAfterSequenceOrder(pipeline);

            insertStepAfter(candidateId, normalizedNew, insertAfterSequenceOrder);
            cleanupDefaultTemplateStepsIfNeeded(candidateId, normalizedNew);
        }
    }

    /**
     * Remove pre-seeded default template rows (invisible steps such as
     * INTERVIEW_SCHEDULES and DISPOSITION) once the candidate reaches Make Offer.
     */
    private void cleanupDefaultTemplateStepsIfNeeded(Long candidateId, String newStatusKey) {
        if (MasterStatus.OFFER_PENDING.name().equalsIgnoreCase(newStatusKey)) {
            pipelineRepository.deleteInvisibleStepsByCandidateId(candidateId);
        }
    }

    private void cleanupDefaultTemplateStepsIfNeeded(Long candidateId, MasterStatus newStatus) {
        if (newStatus != null) {
            cleanupDefaultTemplateStepsIfNeeded(candidateId, newStatus.name());
        }
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

    /**
     * When appending a new interview round, insert after the last engaged step
     * (completed, failed, current, or skipped) so a later round appears after
     * any cancelled or completed round rather than before it.
     */
    private int resolveInsertAfterForNewRound(List<CandidateStepPipeline> pipeline) {
        return pipeline.stream()
                .filter(step -> step.getStepStatus() != PipelineStepStatus.PENDING)
                .map(CandidateStepPipeline::getSequenceOrder)
                .max(Comparator.naturalOrder())
                .orElseGet(() -> resolveInsertAfterSequenceOrder(pipeline));
    }
}
