package com.nemal.service;

import com.nemal.dto.AddCandidateTechnologyDto;
import com.nemal.dto.CandidateTechnologyDto;
import com.nemal.entity.Candidate;
import com.nemal.entity.CandidateTechnology;
import com.nemal.entity.Technology;
import com.nemal.repository.CandidateRepository;
import com.nemal.repository.CandidateTechnologyRepository;
import com.nemal.repository.TechnologyRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class CandidateTechnologyService {

    private final CandidateRepository candidateRepository;
    private final CandidateTechnologyRepository candidateTechnologyRepository;
    private final TechnologyRepository technologyRepository;

    public CandidateTechnologyService(
            CandidateRepository candidateRepository,
            CandidateTechnologyRepository candidateTechnologyRepository,
            TechnologyRepository technologyRepository
    ) {
        this.candidateRepository = candidateRepository;
        this.candidateTechnologyRepository = candidateTechnologyRepository;
        this.technologyRepository = technologyRepository;
    }

    @Transactional(readOnly = true)
    public List<CandidateTechnologyDto> getCandidateTechnologies(Long candidateId) {
        ensureActiveCandidate(candidateId);
        return candidateTechnologyRepository.findByCandidateId(candidateId).stream()
                .map(CandidateTechnologyDto::from)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public Map<Long, List<CandidateTechnologyDto>> getTechnologiesByCandidateIds(Collection<Long> candidateIds) {
        if (candidateIds == null || candidateIds.isEmpty()) {
            return Collections.emptyMap();
        }
        return candidateTechnologyRepository.findByCandidateIdIn(candidateIds).stream()
                .collect(Collectors.groupingBy(
                        ct -> ct.getCandidate().getId(),
                        Collectors.mapping(CandidateTechnologyDto::from, Collectors.toList())
                ));
    }

    @Transactional
    public CandidateTechnologyDto addCandidateTechnology(Long candidateId, AddCandidateTechnologyDto dto) {
        Candidate candidate = ensureActiveCandidate(candidateId);
        Technology technology = technologyRepository.findById(dto.technologyId())
                .orElseThrow(() -> new RuntimeException("Technology not found"));

        boolean exists = candidateTechnologyRepository.existsByCandidateIdAndTechnologyId(
                candidateId,
                dto.technologyId()
        );
        if (exists) {
            throw new IllegalArgumentException("This technology is already added to the candidate profile");
        }

        CandidateTechnology ct = CandidateTechnology.builder()
                .candidate(candidate)
                .technology(technology)
                .isActive(true)
                .build();

        ct = candidateTechnologyRepository.save(ct);
        return CandidateTechnologyDto.from(ct);
    }

    @Transactional
    public void removeCandidateTechnology(Long candidateId, Long candidateTechnologyId) {
        ensureActiveCandidate(candidateId);
        CandidateTechnology ct = candidateTechnologyRepository.findById(candidateTechnologyId)
                .orElseThrow(() -> new RuntimeException("Technology assignment not found"));

        if (!ct.getCandidate().getId().equals(candidateId)) {
            throw new IllegalArgumentException("Unauthorized");
        }

        candidateTechnologyRepository.delete(ct);
    }

    private Candidate ensureActiveCandidate(Long candidateId) {
        return candidateRepository.findByIdAndIsActiveTrue(candidateId)
                .orElseThrow(() -> new RuntimeException("Candidate not found"));
    }
}
