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


    public List<CandidateStepPipeline> getPipelineForCandidate(Long candidateId) {
        return pipelineRepository.findByCandidateIdOrderBySequenceOrderAsc(candidateId);
    }

    /**
     * Initializes the default boilerplate route for brand new candidate profiles
     */
    @Transactional
    public void initializeDefaultPipeline(Long candidateId) {
        Candidate candidate = candidateRepository.findById(candidateId)
                .orElseThrow(() -> new RuntimeException("Candidate record profile not found"));

        // Safeguard preventing multiple dynamic tracks on a single profile
        if (!pipelineRepository.findByCandidateIdOrderBySequenceOrderAsc(candidateId).isEmpty()) {
            return;
        }

        // Standard operational pipeline layout path

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
                .customLabel(customLabel != null && !customLabel.trim().isEmpty() ? "CUSTOM_" + customLabel : "CUSTOM_ROUND")
                .stepStatus(PipelineStepStatus.CURRENT) // Always defaults to CURRENT when inserted dynamically
                .build();

        pipelineRepository.save(newCustomRound);
    }


}
