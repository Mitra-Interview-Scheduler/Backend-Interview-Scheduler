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

import java.util.List;

@Service
public class CandidateStepPipelineService {


    private final CandidateStepPipelineRepository pipelineRepository;
    private final CandidateRepository candidateRepository;
    private final MasterStepRepository masterStepRepository;

    public CandidateStepPipelineService(CandidateStepPipelineRepository pipelineRepository, CandidateRepository candidateRepository, MasterStepRepository masterStepRepository) {
        this.pipelineRepository = pipelineRepository;
        this.candidateRepository = candidateRepository;
        this.masterStepRepository = masterStepRepository;
    }


    @Transactional
    public List<CandidateStepPipeline> getPipelineForCandidate(Long candidateId) {
        return pipelineRepository.findByCandidateIdOrderBySequenceOrderAsc(candidateId);
    }

    /**
     * Initializes the default boilerplate route for brand-new candidate profiles
     */
    @Transactional
    public void initializeDefaultPipeline(Long candidateId) {
        Candidate candidate = candidateRepository.findById(candidateId)
                .orElseThrow(() -> new RuntimeException("Candidate record profile not found"));

        // Safeguard preventing multiple dynamic tracks on a single profile
        if (!pipelineRepository.findByCandidateIdOrderBySequenceOrderAsc(candidateId).isEmpty()) {
            return;
        }


        List<MasterStep> masterSteps = masterStepRepository.findByIsDefaultStepTrueOrderByStepOrderAsc();
        if (masterSteps.isEmpty()) {
            throw  new RuntimeException("Master step type not configured");
        }

        for (MasterStep masterStep : masterSteps) {
            CandidateStepPipeline stepInstance = CandidateStepPipeline.builder()
                    .candidate(candidate)
                    .step(masterStep) // Passes object instead of string key
                    .sequenceOrder(masterStep.getStepOrder())
                    .customLabel("defaultLabels")
                    .stepStatus(masterStep.getStepOrder().equals(1)? PipelineStepStatus.CURRENT : PipelineStepStatus.PENDING)
                    .build();

            pipelineRepository.save(stepInstance);
            System.out.println("pipeline saved for :"+ candidateId);
        }
   }


/**
 * Handles candidate step failure and skips remaining rounds
 */
@Transactional
public void handleStepFailure(Long candidateId, Integer sequenceOrder) {
    // 1. Set targeted active step state to FAILED
    pipelineRepository.updateStepStatus(candidateId, sequenceOrder, PipelineStepStatus.FAILED);

    // 2. Cascade change to downstream pending steps
    pipelineRepository.skipUpcomingSteps(candidateId, sequenceOrder);

    // 3. Sync and adjust the primary candidate state value to REJECTED
    Candidate candidate =  candidateRepository.findById(candidateId)
            .orElseThrow(() -> new RuntimeException("Candidate record profile not found"));
    candidate.setStatus(MasterStatus.REJECTED);
    candidateRepository.save(candidate);
}

/**
 * Completes the current step and advances the candidate to the next one
 */
@Transactional
public void completeStepAndAdvance(Long candidateId, Integer currentSequenceOrder) {
    // Mark current active node as complete
    pipelineRepository.updateStepStatus(candidateId, currentSequenceOrder, PipelineStepStatus.COMPLETED);

    // Set the immediate adjacent step to active status
    pipelineRepository.updateStepStatus(candidateId, currentSequenceOrder + 1, PipelineStepStatus.CURRENT);
}



    /**
     * Inserts a brand new milestone or interview round into a candidate's pipeline tracking map.
     * Shifts all subsequent rounds down by +1 to accommodate the insertion slot.
     */
    @Transactional
    public void insertStepIntoPipeline(Long candidateId, String statusKey, Integer targetSequenceOrder, String customLabel) {
        Candidate candidate = candidateRepository.findById(candidateId)
                .orElseThrow(() -> new RuntimeException("Candidate record profile not found"));

        // Ensure your MasterStepRepository contains a lookup variant for status key strings
        MasterStep masterStep = masterStepRepository.findByStatusKey(statusKey);

        if (masterStep == null) {
            throw new RuntimeException("Master step type configuration not found for: " + statusKey);
        }
        // 1. Shift all rows matching or exceeding our index target position up by 1
        pipelineRepository.shiftSequenceOrdersUp(candidateId, targetSequenceOrder);

        pipelineRepository.updateStepStatus(candidateId, targetSequenceOrder-1, PipelineStepStatus.COMPLETED);


        // 2. Assign and insert the new dynamic round into the newly vacated index position
        CandidateStepPipeline newCustomRound = CandidateStepPipeline.builder()
                .candidate(candidate)
                .step(masterStep)
                .sequenceOrder(targetSequenceOrder)
                .customLabel(customLabel != null && !customLabel.trim().isEmpty() ?  customLabel : "CUSTOM_ROUND")
                .stepStatus(PipelineStepStatus.CURRENT) // Always defaults to CURRENT when inserted dynamically
                .build();

        pipelineRepository.save(newCustomRound);
    }


    /**
     * Synchronizes all pipeline step statuses when the macro candidate status is manually changed
     */
    @Transactional
    public void updatePipelineOnStatusChange(Long candidateId, MasterStatus newStatus) {
        List<CandidateStepPipeline> pipeline = pipelineRepository.findByCandidateIdOrderBySequenceOrderAsc(candidateId);
        if (pipeline.isEmpty()) {return;}

        // 1. Try to find the pipeline entry that corresponds to the new status key
        CandidateStepPipeline targetStep = pipeline.stream()
                .filter(p -> p.getStep() != null && p.getStep().getStatusKey().equalsIgnoreCase(newStatus.name()))
                .findFirst()
                .orElse(null);

        if (targetStep != null) {
            int targetOrder = targetStep.getSequenceOrder();

            // 2. Adjust states relative to the target step's sequence position
            for (CandidateStepPipeline stepInstance : pipeline) {
                if (stepInstance.getSequenceOrder() < targetOrder) {
                    // All past steps are marked completed
                    stepInstance.setStepStatus(PipelineStepStatus.COMPLETED);
                } else if (stepInstance.getSequenceOrder().equals(targetOrder)) {
                    // The matching step becomes the active one
                    stepInstance.setStepStatus(PipelineStepStatus.CURRENT);
                } else {
                    // All upcoming steps shift back to pending
                    stepInstance.setStepStatus(PipelineStepStatus.PENDING);
                }
                pipelineRepository.save(stepInstance);
            }
        } else {
            // 3. Fallback fallback logic for terminal/negative statuses like REJECTED
            // if they are handled as an overall state rather than a sequential line item
            MasterStep masterStep = masterStepRepository.findByStatusKey(newStatus.name());
            if (masterStep == null) {
                // If it's a structural state like REJECTED without an explicit workflow row, fail the current step
                if (newStatus == MasterStatus.REJECTED) {
                    pipeline.stream()
                            .filter(p -> p.getStepStatus() == PipelineStepStatus.CURRENT)
                            .findFirst()
                            .ifPresent(currentActiveStep ->
                                    handleStepFailure(candidateId, currentActiveStep.getSequenceOrder())
                            );
                }
                return;
            }
            int targetSequenceOrder = 1;
            if (!pipeline.isEmpty()) {
                CandidateStepPipeline currentActive = pipeline.stream()
                        .filter(p -> p.getStepStatus() == PipelineStepStatus.CURRENT)
                        .findFirst()
                        .orElse(null);

                if (currentActive != null) {
                    // Insert immediately after whatever round they are currently on
                    targetSequenceOrder = currentActive.getSequenceOrder() + 1;
                } else {
                    // If no step is active, append it to the absolute end of the list
                    targetSequenceOrder = pipeline.get(pipeline.size() - 1).getSequenceOrder() + 1;
                }
            }

            // Reuse your existing dynamic pipeline insertion and row-shifting method!
            insertStepIntoPipeline(candidateId, newStatus.name(), targetSequenceOrder, masterStep.getLabel());

            // Re-fetch the modified layout to reconcile status states (COMPLETED, CURRENT, PENDING)
            List<CandidateStepPipeline> updatedPipeline = pipelineRepository.findByCandidateIdOrderBySequenceOrderAsc(candidateId);
            for (CandidateStepPipeline stepInstance : updatedPipeline) {
                if (stepInstance.getSequenceOrder() < targetSequenceOrder) {
                    stepInstance.setStepStatus(PipelineStepStatus.COMPLETED);
                } else if (stepInstance.getSequenceOrder().equals(targetSequenceOrder)) {
                    stepInstance.setStepStatus(PipelineStepStatus.CURRENT);
                } else {
                    stepInstance.setStepStatus(PipelineStepStatus.PENDING);
                }
                pipelineRepository.save(stepInstance);
            }
        }
    }
}
