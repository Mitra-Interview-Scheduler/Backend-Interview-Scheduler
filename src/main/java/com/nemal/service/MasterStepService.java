package com.nemal.service;

import com.nemal.dto.CandidateStepDto;
import com.nemal.entity.Candidate;
import com.nemal.entity.MasterStep;
import com.nemal.enums.MasterStatus;
import com.nemal.repository.MasterStepRepository;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

@Service
public class MasterStepService {
    private final MasterStepRepository masterStepRepository;

    public MasterStepService(MasterStepRepository masterStepRepository) {
        this.masterStepRepository = masterStepRepository;
    }

    public List<CandidateStepDto> getActiveAndVisibleCandidateSteps() {
        return masterStepRepository.findAllByIsActiveTrueAndIsVisibleTrueOrderByStepOrderAsc()
                .stream()
                .map(CandidateStepDto::from)
                .collect(Collectors.toList());
    }

    /** All active steps including invisible ones (e.g. Interview Schedules) — for admin mapping UIs. */
    public List<CandidateStepDto> getActiveCandidateSteps() {
        return masterStepRepository.findByIsActiveTrueOrderByStepOrderAscDisplayOrderAsc()
                .stream()
                .map(CandidateStepDto::from)
                .collect(Collectors.toList());
    }

    public List<CandidateStepDto> getClosingCandidateSteps() {
        return masterStepRepository.findAllByIsActiveTrueAndIsClosingStepTrueAndIsVisibleTrueOrderByDisplayOrderAsc()
                .stream()
                .map(CandidateStepDto::from)
                .collect(Collectors.toList());
    }

    public MasterStep requireByStatus(MasterStatus status) {
        return requireByStatusKey(status.name());
    }

    public MasterStep requireByStatusKey(String statusKey) {
        if (statusKey == null || statusKey.isBlank()) {
            throw new RuntimeException("Master step status key is required");
        }
        MasterStep step = masterStepRepository.findByStatusKey(statusKey.trim().toUpperCase());
        if (step == null) {
            throw new RuntimeException("Master step not configured for status: " + statusKey);
        }
        return step;
    }

    public void assignStatus(Candidate candidate, MasterStatus status) {
        candidate.setMasterStep(requireByStatus(status));
    }

    public void assignByStatusKey(Candidate candidate, String statusKey) {
        candidate.setMasterStep(requireByStatusKey(statusKey));
    }
}
