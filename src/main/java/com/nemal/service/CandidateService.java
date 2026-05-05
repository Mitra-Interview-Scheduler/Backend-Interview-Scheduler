package com.nemal.service;

import com.nemal.dto.CandidateDto;
import com.nemal.dto.CreateCandidateDto;
import com.nemal.dto.PaginatedResponseDto;
import com.nemal.dto.UpdateCandidateDto;
import com.nemal.entity.Candidate;
import com.nemal.entity.Department;
import com.nemal.entity.Designation;
import com.nemal.enums.CandidateStatus;
import com.nemal.repository.CandidateRepository;
import com.nemal.repository.DepartmentRepository;
import com.nemal.repository.DesignationRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class CandidateService {

    private final CandidateRepository  candidateRepository;
    private final DepartmentRepository departmentRepository;
    private final DesignationRepository designationRepository;

    public CandidateService(
            CandidateRepository  candidateRepository,
            DepartmentRepository departmentRepository,
            DesignationRepository designationRepository
    ) {
        this.candidateRepository  = candidateRepository;
        this.departmentRepository = departmentRepository;
        this.designationRepository = designationRepository;
    }

    // ── Reads ─────────────────────────────────────────────────────────────────

    public List<CandidateDto> getAllCandidates() {
        return candidateRepository.findByIsActiveTrueOrderByAppliedAtDesc()
                .stream().map(CandidateDto::from).collect(Collectors.toList());
    }

    public CandidateDto getCandidateById(Long id) {
        Candidate candidate = candidateRepository.findByIdAndIsActiveTrue(id)
                .orElseThrow(() -> new RuntimeException("Candidate not found"));
        return CandidateDto.from(candidate);
    }

    public List<CandidateDto> getCandidatesByDepartment(Long departmentId) {
        return candidateRepository.findByDepartmentIdAndIsActiveTrueOrderByAppliedAtDesc(departmentId)
                .stream().map(CandidateDto::from).collect(Collectors.toList());
    }

    public List<CandidateDto> getCandidatesByStatus(CandidateStatus status) {
        return candidateRepository.findByStatusAndIsActiveTrueOrderByAppliedAtDesc(status)
                .stream().map(CandidateDto::from).collect(Collectors.toList());
    }

    public List<CandidateDto> searchCandidates(String searchTerm) {
        return candidateRepository.searchCandidates(searchTerm)
                .stream().map(CandidateDto::from).collect(Collectors.toList());
    }

    public List<CandidateDto> findWithFilters(Long departmentId, CandidateStatus status, String searchTerm) {
        List<Candidate> candidates;

        if (departmentId == null && status == null && (searchTerm == null || searchTerm.trim().isEmpty())) {
            candidates = candidateRepository.findByIsActiveTrueOrderByAppliedAtDesc();
        } else if (departmentId != null && status == null && (searchTerm == null || searchTerm.trim().isEmpty())) {
            candidates = candidateRepository.findByDepartmentIdAndIsActiveTrueOrderByAppliedAtDesc(departmentId);
        } else if (departmentId == null && status != null && (searchTerm == null || searchTerm.trim().isEmpty())) {
            candidates = candidateRepository.findByStatusAndIsActiveTrueOrderByAppliedAtDesc(status);
        } else if (departmentId != null && status != null && (searchTerm == null || searchTerm.trim().isEmpty())) {
            candidates = candidateRepository.findByDepartmentIdAndStatusAndIsActiveTrueOrderByAppliedAtDesc(departmentId, status);
        } else {
            String term = (searchTerm != null) ? searchTerm.trim() : "";
            candidates = term.isEmpty()
                    ? candidateRepository.findByIsActiveTrueOrderByAppliedAtDesc()
                    : candidateRepository.searchCandidates(term);

            if (departmentId != null) {
                final Long deptId = departmentId;
                candidates = candidates.stream()
                        .filter(c -> c.getDepartment() != null && c.getDepartment().getId().equals(deptId))
                        .collect(Collectors.toList());
            }
            if (status != null) {
                final CandidateStatus st = status;
                candidates = candidates.stream()
                        .filter(c -> c.getStatus() == st)
                        .collect(Collectors.toList());
            }
        }

        return candidates.stream().map(CandidateDto::from).collect(Collectors.toList());
    }

    public PaginatedResponseDto<CandidateDto> findWithFiltersPaged(
            Long departmentId,
            CandidateStatus status,
            String searchTerm,
            int page,
            int size
    ) {
        List<CandidateDto> all = findWithFilters(departmentId, status, searchTerm);
        int safeSize = Math.max(1, size);
        int safePage = Math.max(0, page);
        int fromIndex = safePage * safeSize;

        if (fromIndex >= all.size()) {
            return new PaginatedResponseDto<>(
                    Collections.emptyList(),
                    safePage,
                    safeSize,
                    all.size(),
                    Math.max(1, (int) Math.ceil((double) all.size() / safeSize)),
                    safePage == 0,
                    true
            );
        }

        int toIndex = Math.min(fromIndex + safeSize, all.size());
        int totalPages = Math.max(1, (int) Math.ceil((double) all.size() / safeSize));

        return new PaginatedResponseDto<>(
                all.subList(fromIndex, toIndex),
                safePage,
                safeSize,
                all.size(),
                totalPages,
                safePage == 0,
                safePage >= totalPages - 1
        );
    }

    // ── Mutations ─────────────────────────────────────────────────────────────

    @Transactional
    public CandidateDto createCandidate(CreateCandidateDto dto) {
        // ── Global email uniqueness (includes soft-deleted rows) ──────────────
        String normalizedEmail = dto.email().trim().toLowerCase();
        if (candidateRepository.existsByEmail(normalizedEmail)) {
            throw new IllegalArgumentException(
                    "A candidate with email '" + dto.email() + "' already exists.");
        }

        Department department = null;
        if (dto.departmentId() != null) {
            department = departmentRepository.findById(dto.departmentId())
                    .orElseThrow(() -> new RuntimeException("Department not found"));
        }

        Designation designation = null;
        if (dto.targetDesignationId() != null) {
            designation = designationRepository.findById(dto.targetDesignationId())
                    .orElseThrow(() -> new RuntimeException("Designation not found"));

            if (department != null && designation.getDepartment() != null
                    && !designation.getDepartment().getId().equals(department.getId())) {
                throw new IllegalArgumentException(
                        "Designation does not belong to the selected department");
            }
            if (department == null && designation.getDepartment() != null) {
                department = designation.getDepartment();
            }
        }

        Candidate candidate = Candidate.builder()
                .name(dto.name().trim())
                .email(normalizedEmail)
                .phone(dto.phone())
                .department(department)
                .targetDesignation(designation)
                .status(CandidateStatus.APPLIED)
                .resumeUrl(dto.resumeUrl())
                .jdUrl(dto.jdUrl())
                .jobReferenceCode(dto.jobReferenceCode())
                .location(dto.location())
                .notes(dto.notes())
                .yearsOfExperience(dto.yearsOfExperience())
                .isActive(true)
                .build();

        candidate = candidateRepository.save(candidate);
        return CandidateDto.from(candidate);
    }

    @Transactional
    public CandidateDto updateCandidate(Long id, UpdateCandidateDto dto) {
        Candidate candidate = candidateRepository.findByIdAndIsActiveTrue(id)
                .orElseThrow(() -> new RuntimeException("Candidate not found"));

        if (dto.name() != null) {
            candidate.setName(dto.name().trim());
        }

        if (dto.email() != null) {
            String normalizedEmail = dto.email().trim().toLowerCase();
            if (!normalizedEmail.equals(candidate.getEmail())) {
                // Global uniqueness: reject if another row (any active state) has this email
                if (candidateRepository.existsByEmailIgnoreCaseAndIdNot(normalizedEmail, id)) {
                    throw new IllegalArgumentException(
                            "A candidate with email '" + dto.email() + "' already exists.");
                }
                candidate.setEmail(normalizedEmail);
            }
        }

        if (dto.phone() != null)              candidate.setPhone(dto.phone());
        if (dto.jdUrl() != null)              candidate.setJdUrl(dto.jdUrl());
        if (dto.jobReferenceCode() != null)   candidate.setJobReferenceCode(dto.jobReferenceCode());
        if (dto.location() != null)           candidate.setLocation(dto.location());
        if (dto.notes() != null)              candidate.setNotes(dto.notes());
        if (dto.yearsOfExperience() != null)  candidate.setYearsOfExperience(dto.yearsOfExperience());
        if (dto.resumeUrl() != null)          candidate.setResumeUrl(dto.resumeUrl());
        if (dto.status() != null)             candidate.setStatus(dto.status());
        if (dto.isActive() != null)           candidate.setActive(dto.isActive());

        if (dto.departmentId() != null) {
            Department department = departmentRepository.findById(dto.departmentId())
                    .orElseThrow(() -> new RuntimeException("Department not found"));
            candidate.setDepartment(department);
        }

        if (dto.targetDesignationId() != null) {
            Designation designation = designationRepository.findById(dto.targetDesignationId())
                    .orElseThrow(() -> new RuntimeException("Designation not found"));

            if (candidate.getDepartment() != null && designation.getDepartment() != null
                    && !designation.getDepartment().getId().equals(candidate.getDepartment().getId())) {
                throw new IllegalArgumentException(
                        "Designation does not belong to the candidate's department");
            }
            candidate.setTargetDesignation(designation);
        }

        candidate = candidateRepository.save(candidate);
        return CandidateDto.from(candidate);
    }

    @Transactional
    public void deleteCandidate(Long id) {
        Candidate candidate = candidateRepository.findByIdAndIsActiveTrue(id)
                .orElseThrow(() -> new RuntimeException("Candidate not found"));
        candidate.setActive(false);
        candidateRepository.save(candidate);
    }
}