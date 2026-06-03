package com.nemal.service;

import com.nemal.dto.CandidateStepDto;
import com.nemal.repository.CandidateStepRepository;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

@Service
public class CandidateStepService {
    private final CandidateStepRepository candidateStepRepository;

    public CandidateStepService(CandidateStepRepository candidateStepRepository) {
        this.candidateStepRepository = candidateStepRepository;
    }

    public List<CandidateStepDto> getActiveCandidateSteps() {
        return candidateStepRepository.findByIsActiveTrueOrderByStepOrderAscDisplayOrderAsc()
                .stream()
                .map(CandidateStepDto::from)
                .collect(Collectors.toList());
    }
}
