package com.nemal.service;

import com.nemal.dto.CandidateStepDto;
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

    public List<CandidateStepDto> getClosingCandidateSteps() {
        return masterStepRepository.findAllByIsActiveTrueAndIsClosingStepTrueOrderByDisplayOrderAsc()
                .stream()
                .map(CandidateStepDto::from)
                .collect(Collectors.toList());
    }
}
