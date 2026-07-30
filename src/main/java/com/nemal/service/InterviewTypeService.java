package com.nemal.service;

import com.nemal.dto.CreateInterviewTypeDto;
import com.nemal.dto.InterviewTypeDeletePreviewDto;
import com.nemal.dto.InterviewTypeDeleteResultDto;
import com.nemal.dto.InterviewTypeDto;
import com.nemal.dto.InterviewTypeFilterRulesDto;
import com.nemal.dto.ResolvedInterviewerFiltersDto;
import com.nemal.dto.UpdateInterviewTypeDto;
import com.nemal.entity.Candidate;
import com.nemal.entity.Designation;
import com.nemal.entity.InterviewType;
import com.nemal.entity.MasterStep;
import com.nemal.entity.Technology;
import com.nemal.entity.Tier;
import com.nemal.enums.InterviewerFilterMode;
import com.nemal.enums.MasterStatus;
import com.nemal.repository.CandidateDomainRepository;
import com.nemal.repository.CandidateRepository;
import com.nemal.repository.CandidateTechnologyRepository;
import com.nemal.repository.DesignationRepository;
import com.nemal.repository.InterviewScheduleRepository;
import com.nemal.repository.InterviewTypeRepository;
import com.nemal.repository.MasterStepRepository;
import com.nemal.repository.TechnologyRepository;
import com.nemal.repository.TierRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class InterviewTypeService {

    private static final MasterStatus DEFAULT_ROUND_STATUS = MasterStatus.INTERVIEW_SCHEDULES;
    private static final MasterStatus DEFAULT_CANCEL_RESTORE_STATUS = MasterStatus.SCREENING;
    public static final String DEFAULT_CODE = "TECHNICAL";

    private static final Set<String> SYSTEM_ROUND_KEYS = Set.of(
            MasterStatus.TECHNICAL_ROUND.name(),
            MasterStatus.HR_ROUND.name(),
            MasterStatus.INTERVIEW_SCHEDULES.name()
    );

    private final InterviewTypeRepository repository;
    private final InterviewScheduleRepository scheduleRepository;
    private final MasterStepRepository masterStepRepository;
    private final CandidateRepository candidateRepository;
    private final CandidateTechnologyRepository candidateTechnologyRepository;
    private final CandidateDomainRepository candidateDomainRepository;
    private final TierRepository tierRepository;
    private final DesignationRepository designationRepository;
    private final TechnologyRepository technologyRepository;

    public InterviewTypeService(
            InterviewTypeRepository repository,
            InterviewScheduleRepository scheduleRepository,
            MasterStepRepository masterStepRepository,
            CandidateRepository candidateRepository,
            CandidateTechnologyRepository candidateTechnologyRepository,
            CandidateDomainRepository candidateDomainRepository,
            TierRepository tierRepository,
            DesignationRepository designationRepository,
            TechnologyRepository technologyRepository) {
        this.repository = repository;
        this.scheduleRepository = scheduleRepository;
        this.masterStepRepository = masterStepRepository;
        this.candidateRepository = candidateRepository;
        this.candidateTechnologyRepository = candidateTechnologyRepository;
        this.candidateDomainRepository = candidateDomainRepository;
        this.tierRepository = tierRepository;
        this.designationRepository = designationRepository;
        this.technologyRepository = technologyRepository;
    }

    @Transactional(readOnly = true)
    public List<InterviewTypeDto> getAll() {
        return repository.findAllByOrderByDisplayOrderAscLabelAsc()
                .stream().map(InterviewTypeDto::from).toList();
    }

    @Transactional(readOnly = true)
    public List<InterviewTypeDto> getActive() {
        return repository.findByActiveTrueOrderByDisplayOrderAscLabelAsc()
                .stream().map(InterviewTypeDto::from).toList();
    }

    @Transactional(readOnly = true)
    public String roundStatusKey(String code) {
        return repository.findByCodeIgnoreCase(normalizeCode(code))
                .map(InterviewType::getRoundStatusKey)
                .filter(key -> key != null && !key.isBlank())
                .orElse(DEFAULT_ROUND_STATUS.name());
    }

    @Transactional(readOnly = true)
    public MasterStatus roundStatus(String code) {
        return parseStatus(roundStatusKey(code), DEFAULT_ROUND_STATUS);
    }

    @Transactional(readOnly = true)
    public String cancelRestoreStatusKey(String code) {
        return repository.findByCodeIgnoreCase(normalizeCode(code))
                .map(InterviewType::getCancelRestoreStatusKey)
                .filter(key -> key != null && !key.isBlank())
                .orElse(DEFAULT_CANCEL_RESTORE_STATUS.name());
    }

    @Transactional(readOnly = true)
    public MasterStatus cancelRestoreStatus(String code) {
        return parseStatus(cancelRestoreStatusKey(code), DEFAULT_CANCEL_RESTORE_STATUS);
    }

    @Transactional(readOnly = true)
    public String resolveCode(String code) {
        if (code == null || code.isBlank()) {
            return DEFAULT_CODE;
        }
        String normalized = normalizeCode(code);
        return repository.findByCodeIgnoreCase(normalized)
                .map(InterviewType::getCode)
                .orElseThrow(() -> new RuntimeException("Unknown interview type: " + code));
    }

    /**
     * Whether booking this interview type should create a Google Calendar meeting
     * (Meet link + attachments). Defaults to true when the type is unknown.
     */
    @Transactional(readOnly = true)
    public boolean shouldCreateCalendarMeeting(String code) {
        if (code == null || code.isBlank()) {
            return true;
        }
        return repository.findByCodeIgnoreCase(normalizeCode(code))
                .map(InterviewType::isCreateCalendarMeeting)
                .orElse(true);
    }

    /**
     * Whether scheduling this interview type requires an interviewer availability slot.
     * Defaults to true when the type is unknown.
     */
    @Transactional(readOnly = true)
    public boolean shouldRequireInterviewer(String code) {
        if (code == null || code.isBlank()) {
            return true;
        }
        return repository.findByCodeIgnoreCase(normalizeCode(code))
                .map(InterviewType::isRequiresInterviewer)
                .orElse(true);
    }

    /**
     * Resolves interviewer matching filters for a candidate + interview type,
     * using the type's SAME_AS_CANDIDATE / FIXED / NONE rules.
     */
    @Transactional(readOnly = true)
    public ResolvedInterviewerFiltersDto resolveInterviewerFilters(String interviewTypeCode, Long candidateId) {
        InterviewType type = repository.findByCodeIgnoreCase(normalizeCode(interviewTypeCode))
                .orElseThrow(() -> new RuntimeException("Unknown interview type: " + interviewTypeCode));
        Candidate candidate = candidateRepository.findById(candidateId)
                .orElseThrow(() -> new RuntimeException("Candidate not found: " + candidateId));

        List<Long> departmentIds = null;
        Long deptForDesignation = null;
        switch (mode(type.getDepartmentFilterMode(), InterviewerFilterMode.SAME_AS_CANDIDATE)) {
            case SAME_AS_CANDIDATE -> {
                if (candidate.getDepartment() != null) {
                    departmentIds = List.of(candidate.getDepartment().getId());
                    deptForDesignation = candidate.getDepartment().getId();
                }
            }
            case FIXED -> {
                if (type.getFixedDepartmentId() != null) {
                    departmentIds = List.of(type.getFixedDepartmentId());
                    deptForDesignation = type.getFixedDepartmentId();
                }
            }
            case NONE -> { /* leave null */ }
        }

        Long minTierOrder = null;
        switch (mode(type.getTierFilterMode(), InterviewerFilterMode.SAME_AS_CANDIDATE)) {
            case SAME_AS_CANDIDATE -> {
                if (candidate.getTargetDesignation() != null
                        && candidate.getTargetDesignation().getTier() != null) {
                    minTierOrder = candidate.getTargetDesignation().getTier().getTierOrder() != null
                            ? candidate.getTargetDesignation().getTier().getTierOrder().longValue()
                            : null;
                }
            }
            case FIXED -> {
                if (type.getFixedMinTierId() != null) {
                    Tier tier = tierRepository.findById(type.getFixedMinTierId()).orElse(null);
                    if (tier != null && tier.getTierOrder() != null) {
                        minTierOrder = tier.getTierOrder().longValue();
                    }
                }
            }
            case NONE -> { /* leave null */ }
        }

        Long minLevelOrder = null;
        switch (mode(type.getDesignationFilterMode(), InterviewerFilterMode.SAME_AS_CANDIDATE)) {
            case SAME_AS_CANDIDATE -> {
                if (candidate.getTargetDesignation() != null
                        && candidate.getTargetDesignation().getLevelOrder() != null) {
                    minLevelOrder = candidate.getTargetDesignation().getLevelOrder().longValue();
                }
            }
            case FIXED -> {
                if (type.getFixedMinDesignationId() != null) {
                    Designation designation = designationRepository.findById(type.getFixedMinDesignationId()).orElse(null);
                    if (designation != null && designation.getLevelOrder() != null) {
                        minLevelOrder = designation.getLevelOrder().longValue();
                    }
                }
            }
            case NONE -> { /* leave null */ }
        }

        Integer minYears = type.getMinYearsExperience();
        if (minYears != null && minYears <= 0) {
            minYears = null;
        }

        List<Long> domainIds = resolveIdList(
                mode(type.getDomainFilterMode(), InterviewerFilterMode.SAME_AS_CANDIDATE),
                type.getFixedDomainIds(),
                () -> candidateDomainRepository.findByCandidateId(candidateId).stream()
                        .map(cd -> cd.getDomain() != null ? cd.getDomain().getId() : null)
                        .filter(Objects::nonNull)
                        .distinct()
                        .toList()
        );

        Set<Long> technologyIds = new HashSet<>();
        List<Long> fromTechMode = resolveIdList(
                mode(type.getTechnologyFilterMode(), InterviewerFilterMode.SAME_AS_CANDIDATE),
                type.getFixedTechnologyIds(),
                () -> candidateTechnologyRepository.findByCandidateIdAndIsActiveTrue(candidateId).stream()
                        .map(ct -> ct.getTechnology() != null ? ct.getTechnology().getId() : null)
                        .filter(Objects::nonNull)
                        .distinct()
                        .toList()
        );
        if (fromTechMode != null) {
            technologyIds.addAll(fromTechMode);
        }

        InterviewerFilterMode categoryMode = mode(type.getCategoryFilterMode(), InterviewerFilterMode.NONE);
        if (categoryMode == InterviewerFilterMode.FIXED
                && type.getFixedCategoryIds() != null
                && !type.getFixedCategoryIds().isEmpty()) {
            technologyIds.addAll(
                    technologyRepository.findByIsActiveTrueAndCategory_IdIn(type.getFixedCategoryIds()).stream()
                            .map(Technology::getId)
                            .toList()
            );
        } else if (categoryMode == InterviewerFilterMode.SAME_AS_CANDIDATE) {
            Set<Long> candidateCategoryIds = candidateTechnologyRepository.findByCandidateIdAndIsActiveTrue(candidateId)
                    .stream()
                    .map(ct -> ct.getTechnology() != null && ct.getTechnology().getCategory() != null
                            ? ct.getTechnology().getCategory().getId() : null)
                    .filter(Objects::nonNull)
                    .collect(Collectors.toSet());
            if (!candidateCategoryIds.isEmpty()) {
                technologyIds.addAll(
                        technologyRepository.findByIsActiveTrueAndCategory_IdIn(candidateCategoryIds).stream()
                                .map(Technology::getId)
                                .toList()
                );
            }
        }

        List<Long> techList = technologyIds.isEmpty() ? null : new ArrayList<>(technologyIds);

        return new ResolvedInterviewerFiltersDto(
                departmentIds,
                deptForDesignation,
                minTierOrder,
                minLevelOrder,
                minYears,
                techList,
                domainIds
        );
    }

    @Transactional
    public InterviewTypeDto create(CreateInterviewTypeDto dto) {
        if (dto.label() == null || dto.label().isBlank()) {
            throw new RuntimeException("Interview type label is required");
        }
        String code;
        if (dto.code() != null && !dto.code().isBlank()) {
            code = normalizeCode(dto.code());
            if (repository.existsByCodeIgnoreCase(code)) {
                throw new RuntimeException("Interview type already exists: " + code);
            }
        } else {
            String slug = slugifyFromLabel(dto.label());
            if (slug.isEmpty()) {
                throw new RuntimeException("Interview type label must contain letters or digits to generate a code");
            }
            code = ensureUniqueCode(slug);
        }

        String label = dto.label().trim();
        String roundKey;
        if (dto.roundStatusKey() != null && !dto.roundStatusKey().isBlank()) {
            roundKey = validateStatusKey(dto.roundStatusKey());
        } else {
            roundKey = createRoundMasterStep(code, label);
        }

        InterviewType type = InterviewType.builder()
                .code(code)
                .label(label)
                .description(trimToNull(dto.description()))
                .active(dto.active() == null || dto.active())
                .displayOrder(dto.displayOrder() != null ? dto.displayOrder() : nextDisplayOrder())
                .isSystem(false)
                .roundStatusKey(roundKey)
                .cancelRestoreStatusKey(validateStatusKey(
                        dto.cancelRestoreStatusKey() != null && !dto.cancelRestoreStatusKey().isBlank()
                                ? dto.cancelRestoreStatusKey()
                                : MasterStatus.SCREENING.name()))
                .createCalendarMeeting(dto.createCalendarMeeting() == null || dto.createCalendarMeeting())
                .requiresInterviewer(dto.requiresInterviewer() == null || dto.requiresInterviewer())
                .build();
        applyFilterRules(type, dto.filterRules() != null ? dto.filterRules() : InterviewTypeFilterRulesDto.defaults());

        // Assessment-style types should not create Meet by default unless explicitly enabled
        if (!type.isRequiresInterviewer() && dto.createCalendarMeeting() == null) {
            type.setCreateCalendarMeeting(false);
        }

        return InterviewTypeDto.from(repository.save(type));
    }

    @Transactional
    public InterviewTypeDto update(Long id, UpdateInterviewTypeDto dto) {
        InterviewType type = repository.findById(id)
                .orElseThrow(() -> new RuntimeException("Interview type not found: " + id));

        if (dto.label() != null && !dto.label().isBlank()) {
            type.setLabel(dto.label().trim());
        }
        if (dto.description() != null) {
            type.setDescription(trimToNull(dto.description()));
        }
        if (dto.active() != null) {
            type.setActive(dto.active());
        }
        if (dto.displayOrder() != null) {
            type.setDisplayOrder(dto.displayOrder());
        }
        type.setRoundStatusKey(validateStatusKey(dto.roundStatusKey()));
        type.setCancelRestoreStatusKey(validateStatusKey(dto.cancelRestoreStatusKey()));
        if (dto.createCalendarMeeting() != null) {
            type.setCreateCalendarMeeting(dto.createCalendarMeeting());
        }
        if (dto.requiresInterviewer() != null) {
            type.setRequiresInterviewer(dto.requiresInterviewer());
            if (!dto.requiresInterviewer() && dto.createCalendarMeeting() == null) {
                type.setCreateCalendarMeeting(false);
            }
        }
        if (dto.filterRules() != null) {
            applyFilterRules(type, dto.filterRules());
        }

        return InterviewTypeDto.from(repository.saveAndFlush(type));
    }

    @Transactional
    public InterviewTypeDto reactivate(Long id) {
        InterviewType type = repository.findById(id)
                .orElseThrow(() -> new RuntimeException("Interview type not found: " + id));
        if (type.isActive()) {
            return InterviewTypeDto.from(type);
        }
        type.setActive(true);
        return InterviewTypeDto.from(repository.save(type));
    }

    public InterviewTypeDeletePreviewDto getDeletePreview(Long id) {
        InterviewType type = repository.findById(id)
                .orElseThrow(() -> new RuntimeException("Interview type not found: " + id));
        if (type.isSystem()) {
            throw new RuntimeException("System interview types cannot be deleted");
        }
        long scheduleCount = scheduleRepository.countByInterviewTypeIgnoreCase(type.getCode());
        return new InterviewTypeDeletePreviewDto(type.getId(), type.getLabel(), scheduleCount > 0, scheduleCount);
    }

    @Transactional
    public InterviewTypeDeleteResultDto delete(Long id) {
        InterviewType type = repository.findById(id)
                .orElseThrow(() -> new RuntimeException("Interview type not found: " + id));
        if (type.isSystem()) {
            throw new RuntimeException("System interview types cannot be deleted");
        }
        if (scheduleRepository.existsByInterviewTypeIgnoreCase(type.getCode())) {
            type.setActive(false);
            repository.save(type);
            return new InterviewTypeDeleteResultDto(
                    InterviewTypeDeleteResultDto.ACTION_DEACTIVATED,
                    type.getLabel());
        }
        String roundKey = type.getRoundStatusKey();
        String label = type.getLabel();
        repository.delete(type);
        deactivateOwnedRoundStep(roundKey);
        return new InterviewTypeDeleteResultDto(InterviewTypeDeleteResultDto.ACTION_DELETED, label);
    }

    private void applyFilterRules(InterviewType type, InterviewTypeFilterRulesDto rules) {
        if (type.getFixedDomainIds() == null) type.setFixedDomainIds(new HashSet<>());
        if (type.getFixedCategoryIds() == null) type.setFixedCategoryIds(new HashSet<>());
        if (type.getFixedTechnologyIds() == null) type.setFixedTechnologyIds(new HashSet<>());

        type.setDepartmentFilterMode(mode(rules.departmentFilterMode(), InterviewerFilterMode.SAME_AS_CANDIDATE));
        type.setFixedDepartmentId(
                type.getDepartmentFilterMode() == InterviewerFilterMode.FIXED ? rules.fixedDepartmentId() : null);

        Integer years = rules.minYearsExperience();
        type.setMinYearsExperience(years != null && years > 0 ? years : null);

        type.setTierFilterMode(mode(rules.tierFilterMode(), InterviewerFilterMode.SAME_AS_CANDIDATE));
        type.setFixedMinTierId(
                type.getTierFilterMode() == InterviewerFilterMode.FIXED ? rules.fixedMinTierId() : null);

        type.setDesignationFilterMode(mode(rules.designationFilterMode(), InterviewerFilterMode.SAME_AS_CANDIDATE));
        type.setFixedMinDesignationId(
                type.getDesignationFilterMode() == InterviewerFilterMode.FIXED ? rules.fixedMinDesignationId() : null);

        type.setDomainFilterMode(mode(rules.domainFilterMode(), InterviewerFilterMode.SAME_AS_CANDIDATE));
        replaceIds(type.getFixedDomainIds(),
                type.getDomainFilterMode() == InterviewerFilterMode.FIXED ? rules.fixedDomainIds() : null);

        type.setCategoryFilterMode(mode(rules.categoryFilterMode(), InterviewerFilterMode.NONE));
        replaceIds(type.getFixedCategoryIds(),
                type.getCategoryFilterMode() == InterviewerFilterMode.FIXED ? rules.fixedCategoryIds() : null);

        type.setTechnologyFilterMode(mode(rules.technologyFilterMode(), InterviewerFilterMode.SAME_AS_CANDIDATE));
        replaceIds(type.getFixedTechnologyIds(),
                type.getTechnologyFilterMode() == InterviewerFilterMode.FIXED ? rules.fixedTechnologyIds() : null);

        if (type.getDepartmentFilterMode() == InterviewerFilterMode.FIXED && type.getFixedDepartmentId() == null) {
            throw new RuntimeException("Fixed department is required when department filter mode is FIXED");
        }
        if (type.getTierFilterMode() == InterviewerFilterMode.FIXED && type.getFixedMinTierId() == null) {
            throw new RuntimeException("Fixed minimum tier is required when tier filter mode is FIXED");
        }
        if (type.getDesignationFilterMode() == InterviewerFilterMode.FIXED && type.getFixedMinDesignationId() == null) {
            throw new RuntimeException("Fixed minimum designation is required when designation filter mode is FIXED");
        }
    }

    private void replaceIds(Set<Long> target, List<Long> incoming) {
        if (target == null) {
            return;
        }
        target.clear();
        if (incoming != null) {
            incoming.stream().filter(Objects::nonNull).forEach(target::add);
        }
    }

    private List<Long> resolveIdList(
            InterviewerFilterMode filterMode,
            Set<Long> fixedIds,
            java.util.function.Supplier<List<Long>> sameAsCandidate
    ) {
        return switch (filterMode) {
            case SAME_AS_CANDIDATE -> {
                List<Long> ids = sameAsCandidate.get();
                yield ids == null || ids.isEmpty() ? null : ids;
            }
            case FIXED -> {
                if (fixedIds == null || fixedIds.isEmpty()) yield null;
                yield new ArrayList<>(fixedIds);
            }
            case NONE -> null;
        };
    }

    private InterviewerFilterMode mode(InterviewerFilterMode value, InterviewerFilterMode fallback) {
        return value != null ? value : fallback;
    }

    private String normalizeCode(String code) {
        return code == null ? DEFAULT_CODE : code.trim().toUpperCase(Locale.ROOT);
    }

    private String slugifyFromLabel(String label) {
        if (label == null) {
            return "";
        }
        return label.trim().toUpperCase(Locale.ROOT)
                .replaceAll("[^A-Z0-9]+", "_")
                .replaceAll("^_+|_+$", "")
                .replaceAll("_+", "_");
    }

    private String ensureUniqueCode(String base) {
        if (!repository.existsByCodeIgnoreCase(base)) {
            return base;
        }
        int n = 2;
        while (repository.existsByCodeIgnoreCase(base + "_" + n)) {
            n++;
        }
        return base + "_" + n;
    }

    private int nextDisplayOrder() {
        return repository.findMaxDisplayOrder() + 1;
    }

    private String createRoundMasterStep(String code, String label) {
        String baseKey = code.endsWith("_ROUND") ? code : code + "_ROUND";
        String statusKey = ensureUniqueStatusKey(baseKey);

        MasterStep step = MasterStep.builder()
                .statusKey(statusKey)
                .label(label)
                .stepOrder(3)
                .displayOrder(masterStepRepository.findMaxDisplayOrder() + 10)
                .bgColor("#06b6d4")
                .badgeClass("bg-cyan-100 text-cyan-800 dark:bg-cyan-900 dark:text-cyan-200")
                .lightClass("bg-cyan-100")
                .isActive(true)
                .isClosingStep(false)
                .isDefaultStep(false)
                .isVisible(true)
                .build();
        masterStepRepository.save(step);
        return statusKey;
    }

    private String ensureUniqueStatusKey(String base) {
        if (!masterStepRepository.existsByStatusKeyIgnoreCase(base)) {
            return base;
        }
        int n = 2;
        while (masterStepRepository.existsByStatusKeyIgnoreCase(base + "_" + n)) {
            n++;
        }
        return base + "_" + n;
    }

    private void deactivateOwnedRoundStep(String roundKey) {
        if (roundKey == null || roundKey.isBlank() || SYSTEM_ROUND_KEYS.contains(roundKey)) {
            return;
        }
        MasterStep step = masterStepRepository.findByStatusKey(roundKey);
        if (step == null || step.isClosingStep() || step.isDefaultStep()) {
            return;
        }
        step.setActive(false);
        step.setVisible(false);
        masterStepRepository.save(step);
    }

    private MasterStatus parseStatus(String key, MasterStatus fallback) {
        if (key == null || key.isBlank()) {
            return fallback;
        }
        try {
            return MasterStatus.valueOf(key.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            return fallback;
        }
    }

    private String validateStatusKey(String key) {
        if (key == null || key.isBlank()) {
            return null;
        }
        String normalized = key.trim().toUpperCase(Locale.ROOT);
        if (masterStepRepository.findByStatusKey(normalized) != null) {
            return normalized;
        }
        try {
            MasterStatus.valueOf(normalized);
        } catch (IllegalArgumentException ex) {
            throw new RuntimeException("Invalid pipeline status key: " + key);
        }
        return normalized;
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
