package com.nemal.service;

import com.nemal.dto.CandidateStepDto;
import com.nemal.repository.MasterStepRepository;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

@Service
public class CandidateStepService {
    private final MasterStepRepository masterStepRepository;

    public CandidateStepService(MasterStepRepository masterStepRepository) {
        this.masterStepRepository = masterStepRepository;
    }

    public List<CandidateStepDto> getActiveCandidateSteps() {
        return masterStepRepository.findByIsActiveTrueOrderByStepOrderAscDisplayOrderAsc()
                .stream()
                .map(CandidateStepDto::from)
                .collect(Collectors.toList());
    }
}
