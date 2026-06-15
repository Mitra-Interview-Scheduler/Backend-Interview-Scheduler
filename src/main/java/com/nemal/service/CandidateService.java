package com.nemal.service;

import com.nemal.dto.CandidateDto;
import com.nemal.dto.CandidateDocumentDto;
import com.nemal.dto.CreateCandidateDto;
import com.nemal.dto.PaginatedResponseDto;
import com.nemal.dto.UpdateCandidateDto;
import com.nemal.entity.Candidate;
import com.nemal.entity.CandidateDocument;
import com.nemal.entity.Department;
import com.nemal.entity.Designation;
import com.nemal.enums.MasterStatus;
import com.nemal.repository.CandidateDocumentRepository;
import com.nemal.repository.CandidateRepository;
import com.nemal.repository.DepartmentRepository;
import com.nemal.repository.DesignationRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.Collections;
import java.util.Locale;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class CandidateService {

    private final CandidateRepository  candidateRepository;
    private final CandidateDocumentRepository candidateDocumentRepository;
    private final DepartmentRepository departmentRepository;
    private final DesignationRepository designationRepository;
    private final CandidateStepPipelineService candidateStepPipelineService;
    private static final long MAX_DOCUMENT_BYTES = 10L * 1024L * 1024L;
    private static final Set<String> ALLOWED_CONTENT_TYPES = Set.of(
            "application/pdf",
            "image/heic",      // .heic (Apple High Efficiency Image Format)
            "image/heif",      // .heif
            "image/jpeg",      // .jpg, .jpeg
            "image/png"
    );

    public CandidateService(
            CandidateRepository candidateRepository,
            CandidateDocumentRepository candidateDocumentRepository,
            DepartmentRepository departmentRepository,
            DesignationRepository designationRepository,
            CandidateStepPipelineService candidateStepPipelineService

    ) {
        this.candidateRepository = candidateRepository;
        this.candidateDocumentRepository = candidateDocumentRepository;
        this.departmentRepository = departmentRepository;
        this.designationRepository = designationRepository;
        this.candidateStepPipelineService = candidateStepPipelineService;
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

    @Transactional(readOnly = true)
    public List<CandidateDocumentDto> getCandidateDocuments(Long candidateId) {
        ensureActiveCandidate(candidateId);
        return candidateDocumentRepository.findByCandidateIdOrderByCreatedAtDesc(candidateId)
                .stream().map(CandidateDocumentDto::from).collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public CandidateDocument getCandidateDocumentFile(Long candidateId, Long documentId) {
        ensureActiveCandidate(candidateId);
        CandidateDocument document = candidateDocumentRepository.findByIdAndCandidateId(documentId, candidateId)
                .orElseThrow(() -> new RuntimeException("Candidate document not found"));
        if (document.getFileData().length < 0) {
            throw new RuntimeException("Candidate document data is invalid");
        }
        return document;
    }

    public List<CandidateDto> getCandidatesByDepartment(Long departmentId) {
        return candidateRepository.findByDepartmentIdAndIsActiveTrueOrderByAppliedAtDesc(departmentId)
                .stream().map(CandidateDto::from).collect(Collectors.toList());
    }

    public List<CandidateDto> getCandidatesByStatus(MasterStatus status) {
        return candidateRepository.findByStatusAndIsActiveTrueOrderByAppliedAtDesc(status)
                .stream().map(CandidateDto::from).collect(Collectors.toList());
    }

    public List<CandidateDto> searchCandidates(String searchTerm) {
        return candidateRepository.searchCandidates(searchTerm)
                .stream().map(CandidateDto::from).collect(Collectors.toList());
    }

    public List<CandidateDto> findWithFilters(Long departmentId, MasterStatus status, String searchTerm) {
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
                final MasterStatus st = status;
                candidates = candidates.stream()
                        .filter(c -> c.getStatus() == st)
                        .collect(Collectors.toList());
            }
        }

        return candidates.stream().map(CandidateDto::from).collect(Collectors.toList());
    }

    public PaginatedResponseDto<CandidateDto> findWithFiltersPaged(
            Long departmentId,
            MasterStatus status,
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
                .status(MasterStatus.NEW)
                .resumeUrl(dto.resumeUrl())
                .jdUrl(dto.jdUrl())
                .resourceLink(dto.resourceLink())
                .jobReferenceCode(dto.jobReferenceCode())
            .resourceRequestNumber(dto.resourceRequestNumber())
                .location(dto.location())
                .notes(dto.notes())
                .yearsOfExperience(dto.yearsOfExperience())
                .isActive(true)
                .build();

        candidate = candidateRepository.save(candidate);
        candidateStepPipelineService.initializeDefaultPipeline(candidate.getId());
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
        if (dto.resourceLink() != null)       candidate.setResourceLink(dto.resourceLink());
        if (dto.jobReferenceCode() != null)   candidate.setJobReferenceCode(dto.jobReferenceCode());
        if (dto.resourceRequestNumber() != null) candidate.setResourceRequestNumber(dto.resourceRequestNumber());
        if (dto.location() != null)           candidate.setLocation(dto.location());
        if (dto.notes() != null)              candidate.setNotes(dto.notes());
        if (dto.yearsOfExperience() != null)  candidate.setYearsOfExperience(dto.yearsOfExperience());
        if (dto.resumeUrl() != null)          candidate.setResumeUrl(dto.resumeUrl());
//        if (dto.status() != null)             candidate.setStatus(dto.status());
        if (dto.isActive() != null)           candidate.setActive(dto.isActive());


        if (dto.status() != null) {
            MasterStatus oldStatus = candidate.getStatus();
            // Only execute updates if the status value actually shifted
            if (oldStatus != dto.status()) {
                candidate.setStatus(dto.status());

                // Synchronizes the candidate step entries to mirror this change
                candidateStepPipelineService.updatePipelineOnStatusChange(id, dto.status());
            }
        }

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

    @Transactional
    public CandidateDocumentDto uploadCandidateDocument(Long candidateId, String documentType, MultipartFile file) {
        Candidate candidate = ensureActiveCandidate(candidateId);
        validateDocument(file);

        try {
            CandidateDocument document = CandidateDocument.builder()
                    .candidate(candidate)
                    .documentType(normalizeDocumentType(documentType))
                    .fileName(safeFileName(file.getOriginalFilename()))
                    .contentType(resolveContentType(file))
                    .fileSize(file.getSize())
                    .fileData(file.getBytes())
                    .build();

            return CandidateDocumentDto.from(candidateDocumentRepository.save(document));
        } catch (IOException e) {
            throw new RuntimeException("Failed to read uploaded document", e);
        }
    }

    @Transactional
    public CandidateDocumentDto replaceCandidateDocument(Long candidateId, Long documentId, String documentType, MultipartFile file) {
        ensureActiveCandidate(candidateId);
        validateDocument(file);

        CandidateDocument document = candidateDocumentRepository.findByIdAndCandidateId(documentId, candidateId)
                .orElseThrow(() -> new RuntimeException("Candidate document not found"));

        try {
            document.setDocumentType(normalizeDocumentType(documentType));
            document.setFileName(safeFileName(file.getOriginalFilename()));
            document.setContentType(resolveContentType(file));
            document.setFileSize(file.getSize());
            document.setFileData(file.getBytes());
            return CandidateDocumentDto.from(candidateDocumentRepository.save(document));
        } catch (IOException e) {
            throw new RuntimeException("Failed to read uploaded document", e);
        }
    }

    @Transactional
    public void deleteCandidateDocument(Long candidateId, Long documentId) {
        ensureActiveCandidate(candidateId);
        CandidateDocument document = candidateDocumentRepository.findByIdAndCandidateId(documentId, candidateId)
                .orElseThrow(() -> new RuntimeException("Candidate document not found"));
        candidateDocumentRepository.delete(document);
    }

    private Candidate ensureActiveCandidate(Long candidateId) {
        return candidateRepository.findByIdAndIsActiveTrue(candidateId)
                .orElseThrow(() -> new RuntimeException("Candidate not found"));
    }

    private void validateDocument(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("Document file is required");
        }
        if (file.getSize() > MAX_DOCUMENT_BYTES) {
            throw new IllegalArgumentException("Document file must be 10 MB or smaller");
        }

        String contentType = resolveContentType(file);
        String fileName = safeFileName(file.getOriginalFilename()).toLowerCase(Locale.ROOT);
        boolean allowedExtension = fileName.endsWith(".pdf") || fileName.endsWith(".doc") || fileName.endsWith(".docx");
        if (!ALLOWED_CONTENT_TYPES.contains(contentType) && !allowedExtension) {
            throw new IllegalArgumentException("Only PDF, JPG, JPEG , heic and heif are supported");
        }
    }

    private String normalizeDocumentType(String documentType) {
        String value = documentType == null || documentType.trim().isEmpty()
                ? "OTHER"
                : documentType.trim().toUpperCase(Locale.ROOT);
        return value.length() > 50 ? value.substring(0, 50) : value;
    }

    private String safeFileName(String fileName) {
        if (fileName == null || fileName.trim().isEmpty()) {
            return "candidate-document";
        }
        String cleaned = fileName.replace("\\", "/");
        cleaned = cleaned.substring(cleaned.lastIndexOf('/') + 1).trim();
        return cleaned.length() > 255 ? cleaned.substring(cleaned.length() - 255) : cleaned;
    }

    private String resolveContentType(MultipartFile file) {
        return file.getContentType() == null || file.getContentType().isBlank()
                ? "application/octet-stream"
                : file.getContentType();
    }
}
