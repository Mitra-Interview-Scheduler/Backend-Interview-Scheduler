package com.nemal.service;

import com.nemal.dto.CandidateDto;
import com.nemal.dto.CandidateDocumentDto;
import com.nemal.dto.CreateCandidateDto;
import com.nemal.dto.DepartmentUserDto;
import com.nemal.dto.PaginatedResponseDto;
import com.nemal.dto.UpdateCandidateDto;
import com.nemal.entity.Candidate;
import com.nemal.entity.CandidateDocument;
import com.nemal.entity.Department;
import com.nemal.entity.Designation;
import com.nemal.entity.MasterStep;
import com.nemal.entity.User;
import com.nemal.enums.MasterStatus;
import com.nemal.enums.PipelineAuditActionType;
import com.nemal.repository.CandidateDocumentRepository;
import com.nemal.repository.CandidateRepository;
import com.nemal.repository.DepartmentRepository;
import com.nemal.repository.DesignationRepository;
import com.nemal.repository.MasterStepRepository;
import com.nemal.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.Comparator;
import java.util.Locale;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class CandidateService {

    private final CandidateRepository  candidateRepository;
    private final CandidateDocumentRepository candidateDocumentRepository;
    private final DepartmentRepository departmentRepository;
    private final DesignationRepository designationRepository;
    private final CandidateStepPipelineService candidateStepPipelineService;
    private final MasterStepService masterStepService;
    private final MasterStepRepository masterStepRepository;
    private final CandidateClosureService candidateClosureService;
    private final CandidatePipelineAuditService candidatePipelineAuditService;
    private final UserRepository userRepository;
    private static final long MAX_DOCUMENT_BYTES = 10L * 1024L * 1024L;
    private static final Set<String> ALLOWED_CONTENT_TYPES = Set.of(
            "application/pdf",
            "image/heic",
            "image/heif",
            "image/jpeg",
            "image/png",
            "application/msword",
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
    );

    public CandidateService(
            CandidateRepository candidateRepository,
            CandidateDocumentRepository candidateDocumentRepository,
            DepartmentRepository departmentRepository,
            DesignationRepository designationRepository,
            CandidateStepPipelineService candidateStepPipelineService,
            MasterStepService masterStepService,
            MasterStepRepository masterStepRepository,
            CandidateClosureService candidateClosureService,
            CandidatePipelineAuditService candidatePipelineAuditService,
            UserRepository userRepository

    ) {
        this.candidateRepository = candidateRepository;
        this.candidateDocumentRepository = candidateDocumentRepository;
        this.departmentRepository = departmentRepository;
        this.designationRepository = designationRepository;
        this.candidateStepPipelineService = candidateStepPipelineService;
        this.masterStepService = masterStepService;
        this.masterStepRepository = masterStepRepository;
        this.candidateClosureService = candidateClosureService;
        this.candidatePipelineAuditService = candidatePipelineAuditService;
        this.userRepository = userRepository;
    }

    // ── Reads ─────────────────────────────────────────────────────────────────

    public List<DepartmentUserDto> getCoordinatedHrOptions(Long departmentId) {
        if (departmentId == null) {
            throw new IllegalArgumentException("Department is required");
        }
        departmentRepository.findById(departmentId)
                .orElseThrow(() -> new IllegalArgumentException("Department not found"));
        return userRepository.findByDepartment_IdAndIsActiveTrueOrderByFirstNameAscLastNameAsc(departmentId).stream()
                .filter(User::isActive)
                .sorted(Comparator.comparing(User::getFullName, String.CASE_INSENSITIVE_ORDER))
                .map(DepartmentUserDto::from)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<CandidateDto> getAllCandidates() {
        return candidateRepository.findByIsActiveTrueOrderByAppliedAtDesc()
                .stream().map(CandidateDto::from).collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public CandidateDto getCandidateById(Long id) {
        Candidate candidate = candidateRepository.findByIdAndIsActiveTrue(id)
                .orElseThrow(() -> new RuntimeException("Candidate not found"));
        return CandidateDto.from(candidate, candidateClosureService.getLatestClosure(id));
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
                        .filter(c -> Objects.equals(c.getStatus(), st))
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
        int safeSize = Math.max(1, size);
        int safePage = Math.max(0, page);
        String normalizedSearch = (searchTerm != null && !searchTerm.trim().isEmpty())
                ? searchTerm.trim()
                : null;
        String statusKey = status != null ? status.name() : null;

        Page<Candidate> result = candidateRepository.findWithFiltersPaged(
                departmentId,
                statusKey,
                normalizedSearch,
                PageRequest.of(safePage, safeSize)
        );

        List<CandidateDto> content = result.getContent().stream()
                .map(CandidateDto::from)
                .collect(Collectors.toList());

        return new PaginatedResponseDto<>(
                content,
                result.getNumber(),
                result.getSize(),
                result.getTotalElements(),
                Math.max(1, result.getTotalPages()),
                result.isFirst(),
                result.isLast()
        );
    }

    // ── Mutations ─────────────────────────────────────────────────────────────

    @Transactional
    public CandidateDto createCandidate(CreateCandidateDto dto) {
        if (dto.coordinatedHrId() == null) {
            throw new IllegalArgumentException("Candidate coordinator is required");
        }
        User coordinatedHr = resolveCoordinatedHr(dto.coordinatedHrId());

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
                .resumeUrl(dto.resumeUrl())
                .jdUrl(dto.jdUrl())
                .resourceLink(dto.resourceLink())
                .jobReferenceCode(dto.jobReferenceCode())
            .resourceRequestNumber(dto.resourceRequestNumber())
                .location(dto.location())
                .notes(dto.notes())
                .yearsOfExperience(dto.yearsOfExperience())
                .coordinatedHr(coordinatedHr)
                .isActive(true)
                .build();

        masterStepService.assignStatus(candidate, MasterStatus.NEW);
        candidate = candidateRepository.save(candidate);
        candidateStepPipelineService.initializeDefaultPipeline(candidate.getId());
        return CandidateDto.from(candidate);
    }

    @Transactional
    public CandidateDto updateCandidate(Long id, UpdateCandidateDto dto, User changedBy) {
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
            boolean addPipelineRound = Boolean.TRUE.equals(dto.addPipelineRound());
            boolean statusChanged = !Objects.equals(oldStatus, dto.status());
            boolean shouldSyncPipeline = statusChanged || addPipelineRound;

            if (statusChanged) {
                MasterStep targetStep = masterStepRepository.findByStatusKey(dto.status().name());
                if (targetStep != null && targetStep.isClosingStep() && targetStep.isVisible()) {
                    throw new IllegalArgumentException(
                            "Use Close Application to move the candidate to a closing stage.");
                }
                masterStepService.assignStatus(candidate, dto.status());
            }

            if (shouldSyncPipeline) {
                candidateStepPipelineService.updatePipelineOnStatusChange(
                        id, dto.status(), oldStatus, addPipelineRound);
            }

            if (statusChanged) {
                candidatePipelineAuditService.recordStatusChange(
                        id,
                        dto.status(),
                        oldStatus,
                        PipelineAuditActionType.STATUS_CHANGED,
                        changedBy,
                        null);
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

        if (dto.coordinatedHrId() != null) {
            candidate.setCoordinatedHr(resolveCoordinatedHr(dto.coordinatedHrId()));
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
            throw new IllegalArgumentException("Only PDF, Word (.doc/.docx), JPG, JPEG, PNG, HEIC, and HEIF are supported");
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

    private User resolveCoordinatedHr(Long coordinatedHrId) {
        User user = userRepository.findById(coordinatedHrId)
                .orElseThrow(() -> new IllegalArgumentException("Candidate coordinator not found"));

        if (!user.isActive()) {
            throw new IllegalArgumentException("Candidate coordinator is inactive");
        }

        return user;
    }
}
