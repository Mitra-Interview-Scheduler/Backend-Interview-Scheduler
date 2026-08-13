package com.nemal.service;

import com.nemal.dto.CreateCandidateDto;
import com.nemal.dto.CreateCatalogTypeDto;
import com.nemal.dto.CreateDepartmentDto;
import com.nemal.dto.CreateDesignationDto;
import com.nemal.dto.CreateDomainDto;
import com.nemal.dto.CreateFeedbackQuestionDto;
import com.nemal.dto.CreateInterviewTypeDto;
import com.nemal.dto.CreateQuestionCategoryDto;
import com.nemal.dto.CreateTechnologyCategoryDto;
import com.nemal.dto.CreateTechnologyDto;
import com.nemal.dto.CreateTierDto;
import com.nemal.dto.CandidateDto;
import com.nemal.dto.CatalogTypeDto;
import com.nemal.dto.DomainDto;
import com.nemal.dto.ExcelImportResultDto;
import com.nemal.dto.FeedbackOptionDto;
import com.nemal.dto.TechnologyCategoryDto;
import com.nemal.entity.Department;
import com.nemal.entity.Designation;
import com.nemal.entity.Domain;
import com.nemal.entity.MasterStep;
import com.nemal.entity.Tier;
import com.nemal.entity.User;
import com.nemal.enums.Role;
import com.nemal.repository.DepartmentRepository;
import com.nemal.repository.DesignationRepository;
import com.nemal.repository.DomainRepository;
import com.nemal.repository.MasterStepRepository;
import com.nemal.repository.TierRepository;
import com.nemal.repository.UserRepository;
import com.nemal.util.ExcelHelper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

@Service
public class ExcelImportExportService {

    private final DomainService domainService;
    private final DomainRepository domainRepository;
    private final DepartmentService departmentService;
    private final DepartmentRepository departmentRepository;
    private final TierService tierService;
    private final TierRepository tierRepository;
    private final DesignationService designationService;
    private final DesignationRepository designationRepository;
    private final TechnologyService technologyService;
    private final TechnologyCategoryService technologyCategoryService;
    private final InterviewTypeService interviewTypeService;
    private final DocumentTypeService documentTypeService;
    private final ResourceTypeService resourceTypeService;
    private final CandidateService candidateService;
    private final UserRepository userRepository;
    private final MasterStepRepository masterStepRepository;
    private final FeedbackService feedbackService;
    private final QuestionCategoryService questionCategoryService;

    public ExcelImportExportService(
            DomainService domainService,
            DomainRepository domainRepository,
            DepartmentService departmentService,
            DepartmentRepository departmentRepository,
            TierService tierService,
            TierRepository tierRepository,
            DesignationService designationService,
            DesignationRepository designationRepository,
            TechnologyService technologyService,
            TechnologyCategoryService technologyCategoryService,
            InterviewTypeService interviewTypeService,
            DocumentTypeService documentTypeService,
            ResourceTypeService resourceTypeService,
            CandidateService candidateService,
            UserRepository userRepository,
            MasterStepRepository masterStepRepository,
            FeedbackService feedbackService,
            QuestionCategoryService questionCategoryService
    ) {
        this.domainService = domainService;
        this.domainRepository = domainRepository;
        this.departmentService = departmentService;
        this.departmentRepository = departmentRepository;
        this.tierService = tierService;
        this.tierRepository = tierRepository;
        this.designationService = designationService;
        this.designationRepository = designationRepository;
        this.technologyService = technologyService;
        this.technologyCategoryService = technologyCategoryService;
        this.interviewTypeService = interviewTypeService;
        this.documentTypeService = documentTypeService;
        this.resourceTypeService = resourceTypeService;
        this.candidateService = candidateService;
        this.userRepository = userRepository;
        this.masterStepRepository = masterStepRepository;
        this.feedbackService = feedbackService;
        this.questionCategoryService = questionCategoryService;
    }

    // ── Domains ──────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public byte[] exportDomains() {
        List<String> headers = List.of("name", "code", "isActive");
        List<List<Object>> rows = domainService.getAllDomainsIncludingInactive().stream()
                .map(d -> List.<Object>of(d.name(), nullToEmpty(d.code()), d.isActive()))
                .toList();
        return ExcelHelper.writeSheet("Domains", headers, rows);
    }

    @Transactional
    public ExcelImportResultDto importDomains(MultipartFile file) {
        ExcelHelper.validateExcelFile(file);
        ExcelImportResultDto.Builder result = ExcelImportResultDto.builder();
        try {
            List<Map<String, String>> rows = ExcelHelper.readRows(file.getInputStream());
            int rowNum = 1;
            for (Map<String, String> row : rows) {
                rowNum++;
                try {
                    String name = ExcelHelper.get(row, "name");
                    if (name.isBlank()) {
                        throw new IllegalArgumentException("name is required");
                    }
                    var existing = domainRepository.findByNameIgnoreCase(name);
                    if (existing.isPresent()) {
                        Domain domain = existing.get();
                        if (!domain.isActive()) {
                            domain.setActive(true);
                            domainRepository.save(domain);
                            result.created();
                            result.skipped("Row " + rowNum + ": reactivated existing domain '" + name + "'");
                        } else {
                            result.skipped("Row " + rowNum + ": domain '" + name + "' already exists");
                        }
                        continue;
                    }
                    domainService.createDomain(new CreateDomainDto(name, blankToNull(ExcelHelper.get(row, "code"))));
                    result.created();
                } catch (Exception e) {
                    result.failed("Row " + rowNum + ": " + e.getMessage());
                }
            }
        } catch (Exception e) {
            result.failed(e.getMessage());
        }
        return result.build();
    }

    // ── Departments ──────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public byte[] exportDepartments() {
        List<String> headers = List.of("name", "code");
        List<List<Object>> rows = departmentService.getAllDepartments().stream()
                .map(d -> List.<Object>of(d.name(), nullToEmpty(d.code())))
                .toList();
        return ExcelHelper.writeSheet("Departments", headers, rows);
    }

    @Transactional
    public ExcelImportResultDto importDepartments(MultipartFile file) {
        ExcelHelper.validateExcelFile(file);
        ExcelImportResultDto.Builder result = ExcelImportResultDto.builder();
        try {
            List<Map<String, String>> rows = ExcelHelper.readRows(file.getInputStream());
            int rowNum = 1;
            for (Map<String, String> row : rows) {
                rowNum++;
                try {
                    String name = ExcelHelper.get(row, "name");
                    if (name.isBlank()) {
                        throw new IllegalArgumentException("name is required");
                    }
                    if (departmentRepository.existsByNameIgnoreCase(name)) {
                        result.skipped("Row " + rowNum + ": department '" + name + "' already exists");
                        continue;
                    }
                    departmentService.createDepartment(new CreateDepartmentDto(name, blankToNull(ExcelHelper.get(row, "code"))));
                    result.created();
                } catch (Exception e) {
                    result.failed("Row " + rowNum + ": " + e.getMessage());
                }
            }
        } catch (Exception e) {
            result.failed(e.getMessage());
        }
        return result.build();
    }

    // ── Tiers ────────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public byte[] exportTiers() {
        List<String> headers = List.of("name", "departmentCode", "departmentName", "tierOrder", "description", "isActive");
        List<List<Object>> rows = tierRepository.findAll().stream()
                .map(t -> {
                    Department dept = t.getDepartment();
                    return List.<Object>of(
                            t.getName(),
                            dept != null ? nullToEmpty(dept.getCode()) : "",
                            dept != null ? nullToEmpty(dept.getName()) : "",
                            t.getTierOrder(),
                            nullToEmpty(t.getDescription()),
                            t.isActive()
                    );
                })
                .toList();
        return ExcelHelper.writeSheet("Tiers", headers, rows, Map.of(
                "departmentCode", departmentCodes(),
                "departmentName", departmentNames(),
                "isActive", booleanChoices()
        ));
    }

    @Transactional
    public ExcelImportResultDto importTiers(MultipartFile file) {
        ExcelHelper.validateExcelFile(file);
        ExcelImportResultDto.Builder result = ExcelImportResultDto.builder();
        try {
            List<Map<String, String>> rows = ExcelHelper.readRows(file.getInputStream());
            int rowNum = 1;
            for (Map<String, String> row : rows) {
                rowNum++;
                try {
                    String name = ExcelHelper.get(row, "name");
                    Integer tierOrder = ExcelHelper.getInteger(row, "tierOrder", "tier_order", "order");
                    if (name.isBlank()) {
                        throw new IllegalArgumentException("name is required");
                    }
                    if (tierOrder == null) {
                        throw new IllegalArgumentException("tierOrder is required");
                    }
                    Department department = resolveDepartment(
                            ExcelHelper.get(row, "departmentCode", "department_code"),
                            ExcelHelper.get(row, "departmentName", "department_name", "department")
                    );
                    boolean exists = tierRepository.findByDepartmentIdAndIsActiveTrueOrderByTierOrderAsc(department.getId())
                            .stream()
                            .anyMatch(t -> t.getName().equalsIgnoreCase(name) || Objects.equals(t.getTierOrder(), tierOrder));
                    if (exists) {
                        result.skipped("Row " + rowNum + ": tier already exists in department '" + department.getName() + "'");
                        continue;
                    }
                    tierService.createTier(new CreateTierDto(
                            name,
                            department.getId(),
                            tierOrder,
                            blankToNull(ExcelHelper.get(row, "description"))
                    ));
                    result.created();
                } catch (Exception e) {
                    result.failed("Row " + rowNum + ": " + e.getMessage());
                }
            }
        } catch (Exception e) {
            result.failed(e.getMessage());
        }
        return result.build();
    }

    // ── Designations ─────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public byte[] exportDesignations() {
        List<String> headers = List.of(
                "name", "levelOrder", "departmentCode", "departmentName", "tierName", "tierOrder", "description", "isActive"
        );
        List<List<Object>> rows = designationRepository.findAll().stream()
                .map(d -> {
                    Department dept = d.getDepartment();
                    Tier tier = d.getTier();
                    return List.<Object>of(
                            d.getName(),
                            d.getLevelOrder(),
                            dept != null ? nullToEmpty(dept.getCode()) : "",
                            dept != null ? nullToEmpty(dept.getName()) : "",
                            tier != null ? nullToEmpty(tier.getName()) : "",
                            tier != null ? tier.getTierOrder() : "",
                            nullToEmpty(d.getDescription()),
                            d.isActive()
                    );
                })
                .toList();
        return ExcelHelper.writeSheet("Designations", headers, rows, Map.of(
                "departmentCode", departmentCodes(),
                "departmentName", departmentNames(),
                "tierName", tierNames(),
                "isActive", booleanChoices()
        ));
    }

    @Transactional
    public ExcelImportResultDto importDesignations(MultipartFile file) {
        ExcelHelper.validateExcelFile(file);
        ExcelImportResultDto.Builder result = ExcelImportResultDto.builder();
        try {
            List<Map<String, String>> rows = ExcelHelper.readRows(file.getInputStream());
            int rowNum = 1;
            for (Map<String, String> row : rows) {
                rowNum++;
                try {
                    String name = ExcelHelper.get(row, "name");
                    Integer levelOrder = ExcelHelper.getInteger(row, "levelOrder", "level_order");
                    if (name.isBlank()) {
                        throw new IllegalArgumentException("name is required");
                    }
                    if (levelOrder == null) {
                        throw new IllegalArgumentException("levelOrder is required");
                    }
                    Department department = resolveDepartment(
                            ExcelHelper.get(row, "departmentCode", "department_code"),
                            ExcelHelper.get(row, "departmentName", "department_name", "department")
                    );
                    Tier tier = resolveTier(
                            department.getId(),
                            ExcelHelper.get(row, "tierName", "tier_name", "tier"),
                            ExcelHelper.getInteger(row, "tierOrder", "tier_order")
                    );
                    if (designationRepository.existsByDepartmentIdAndNameIgnoreCaseAndIsActiveTrue(department.getId(), name)) {
                        result.skipped("Row " + rowNum + ": designation '" + name + "' already exists");
                        continue;
                    }
                    designationService.createDesignation(new CreateDesignationDto(
                            name,
                            levelOrder,
                            department.getId(),
                            tier.getId(),
                            blankToNull(ExcelHelper.get(row, "description"))
                    ));
                    result.created();
                } catch (Exception e) {
                    result.failed("Row " + rowNum + ": " + e.getMessage());
                }
            }
        } catch (Exception e) {
            result.failed(e.getMessage());
        }
        return result.build();
    }

    // ── Technology categories ────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public byte[] exportTechnologyCategories() {
        List<String> headers = List.of("code", "label", "displayOrder", "isActive");
        List<List<Object>> rows = technologyCategoryService.getActiveCategories().stream()
                .map(c -> List.<Object>of(nullToEmpty(c.code()), c.label(), c.displayOrder(), c.isActive()))
                .toList();
        return ExcelHelper.writeSheet("TechnologyCategories", headers, rows);
    }

    @Transactional
    public ExcelImportResultDto importTechnologyCategories(MultipartFile file) {
        ExcelHelper.validateExcelFile(file);
        ExcelImportResultDto.Builder result = ExcelImportResultDto.builder();
        try {
            List<Map<String, String>> rows = ExcelHelper.readRows(file.getInputStream());
            int rowNum = 1;
            for (Map<String, String> row : rows) {
                rowNum++;
                try {
                    String label = ExcelHelper.get(row, "label", "name");
                    if (label.isBlank()) {
                        throw new IllegalArgumentException("label is required");
                    }
                    technologyCategoryService.createCategory(new CreateTechnologyCategoryDto(
                            blankToNull(ExcelHelper.get(row, "code")),
                            label,
                            ExcelHelper.getInteger(row, "displayOrder", "display_order", "order")
                    ));
                    result.created();
                } catch (Exception e) {
                    String msg = e.getMessage() == null ? "failed" : e.getMessage();
                    if (msg.toLowerCase(Locale.ROOT).contains("already exists")) {
                        result.skipped("Row " + rowNum + ": " + msg);
                    } else {
                        result.failed("Row " + rowNum + ": " + msg);
                    }
                }
            }
        } catch (Exception e) {
            result.failed(e.getMessage());
        }
        return result.build();
    }

    // ── Technologies ─────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public byte[] exportTechnologies() {
        List<String> headers = List.of("name", "code", "categoryCode", "categoryLabel", "isActive");
        List<List<Object>> rows = technologyService.getAllTechnologies().stream()
                .map(t -> List.<Object>of(
                        t.name(),
                        nullToEmpty(t.code()),
                        t.category() != null ? nullToEmpty(t.category().code()) : "",
                        t.category() != null ? nullToEmpty(t.category().label()) : "",
                        t.isActive()
                ))
                .toList();
        List<TechnologyCategoryDto> categories = technologyCategoryService.getActiveCategories();
        return ExcelHelper.writeSheet("Technologies", headers, rows, Map.of(
                "categoryCode", categories.stream().map(TechnologyCategoryDto::code).toList(),
                "categoryLabel", categories.stream().map(TechnologyCategoryDto::label).toList(),
                "isActive", booleanChoices()
        ));
    }

    @Transactional
    public ExcelImportResultDto importTechnologies(MultipartFile file) {
        ExcelHelper.validateExcelFile(file);
        ExcelImportResultDto.Builder result = ExcelImportResultDto.builder();
        Map<String, TechnologyCategoryDto> categoriesByCode = technologyCategoryService.getActiveCategories().stream()
                .collect(Collectors.toMap(c -> c.code().toLowerCase(Locale.ROOT), c -> c, (a, b) -> a));
        Map<String, TechnologyCategoryDto> categoriesByLabel = technologyCategoryService.getActiveCategories().stream()
                .collect(Collectors.toMap(c -> c.label().toLowerCase(Locale.ROOT), c -> c, (a, b) -> a));
        try {
            List<Map<String, String>> rows = ExcelHelper.readRows(file.getInputStream());
            int rowNum = 1;
            for (Map<String, String> row : rows) {
                rowNum++;
                try {
                    String name = ExcelHelper.get(row, "name");
                    if (name.isBlank()) {
                        throw new IllegalArgumentException("name is required");
                    }
                    String categoryCode = ExcelHelper.get(row, "categoryCode", "category_code");
                    String categoryLabel = ExcelHelper.get(row, "categoryLabel", "category_label", "category");
                    TechnologyCategoryDto category = null;
                    if (!categoryCode.isBlank()) {
                        category = categoriesByCode.get(categoryCode.toLowerCase(Locale.ROOT));
                    }
                    if (category == null && !categoryLabel.isBlank()) {
                        category = categoriesByLabel.get(categoryLabel.toLowerCase(Locale.ROOT));
                    }
                    if (category == null) {
                        throw new IllegalArgumentException("category not found (use categoryCode or categoryLabel)");
                    }
                    technologyService.createTechnology(new CreateTechnologyDto(
                            name,
                            blankToNull(ExcelHelper.get(row, "code")),
                            category.id()
                    ));
                    result.created();
                } catch (Exception e) {
                    String msg = e.getMessage() == null ? "failed" : e.getMessage();
                    if (msg.toLowerCase(Locale.ROOT).contains("already exists")) {
                        result.skipped("Row " + rowNum + ": " + msg);
                    } else {
                        result.failed("Row " + rowNum + ": " + msg);
                    }
                }
            }
        } catch (Exception e) {
            result.failed(e.getMessage());
        }
        return result.build();
    }

    // ── Interview types ──────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public byte[] exportInterviewTypes() {
        List<String> headers = List.of(
                "code", "label", "description", "active", "displayOrder",
                "roundStatusKey", "cancelRestoreStatusKey", "createCalendarMeeting", "requiresInterviewer"
        );
        List<List<Object>> rows = interviewTypeService.getAll().stream()
                .map(t -> List.<Object>of(
                        t.code(),
                        t.label(),
                        nullToEmpty(t.description()),
                        t.active(),
                        t.displayOrder(),
                        nullToEmpty(t.roundStatusKey()),
                        nullToEmpty(t.cancelRestoreStatusKey()),
                        t.createCalendarMeeting(),
                        t.requiresInterviewer()
                ))
                .toList();
        List<String> statusKeys = masterStepKeys();
        return ExcelHelper.writeSheet("InterviewTypes", headers, rows, Map.of(
                "active", booleanChoices(),
                "roundStatusKey", statusKeys,
                "cancelRestoreStatusKey", statusKeys,
                "createCalendarMeeting", booleanChoices(),
                "requiresInterviewer", booleanChoices()
        ));
    }

    @Transactional
    public ExcelImportResultDto importInterviewTypes(MultipartFile file) {
        ExcelHelper.validateExcelFile(file);
        ExcelImportResultDto.Builder result = ExcelImportResultDto.builder();
        try {
            List<Map<String, String>> rows = ExcelHelper.readRows(file.getInputStream());
            int rowNum = 1;
            for (Map<String, String> row : rows) {
                rowNum++;
                try {
                    String label = ExcelHelper.get(row, "label", "name");
                    if (label.isBlank()) {
                        throw new IllegalArgumentException("label is required");
                    }
                    interviewTypeService.create(new CreateInterviewTypeDto(
                            blankToNull(ExcelHelper.get(row, "code")),
                            label,
                            blankToNull(ExcelHelper.get(row, "description")),
                            ExcelHelper.getBoolean(row, "active", "isActive", "is_active"),
                            ExcelHelper.getInteger(row, "displayOrder", "display_order", "order"),
                            blankToNull(ExcelHelper.get(row, "roundStatusKey", "round_status_key")),
                            blankToNull(ExcelHelper.get(row, "cancelRestoreStatusKey", "cancel_restore_status_key")),
                            ExcelHelper.getBoolean(row, "createCalendarMeeting", "create_calendar_meeting"),
                            ExcelHelper.getBoolean(row, "requiresInterviewer", "requires_interviewer"),
                            null
                    ));
                    result.created();
                } catch (Exception e) {
                    String msg = e.getMessage() == null ? "failed" : e.getMessage();
                    if (msg.toLowerCase(Locale.ROOT).contains("already exists")) {
                        result.skipped("Row " + rowNum + ": " + msg);
                    } else {
                        result.failed("Row " + rowNum + ": " + msg);
                    }
                }
            }
        } catch (Exception e) {
            result.failed(e.getMessage());
        }
        return result.build();
    }

    // ── Document / resource types ────────────────────────────────────────────

    @Transactional(readOnly = true)
    public byte[] exportDocumentTypes() {
        return exportCatalogTypes("DocumentTypes", documentTypeService.listAll());
    }

    @Transactional
    public ExcelImportResultDto importDocumentTypes(MultipartFile file) {
        return importCatalogTypes(file, documentTypeService::create);
    }

    @Transactional(readOnly = true)
    public byte[] exportResourceTypes() {
        return exportCatalogTypes("ResourceTypes", resourceTypeService.listAll());
    }

    @Transactional
    public ExcelImportResultDto importResourceTypes(MultipartFile file) {
        return importCatalogTypes(file, resourceTypeService::create);
    }

    // ── Candidates ───────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public byte[] exportCandidates() {
        List<String> headers = List.of(
                "name", "email", "phone", "departmentCode", "departmentName",
                "designationName", "location", "yearsOfExperience",
                "jobReferenceCode", "resourceRequestNumber", "notes",
                "coordinatedHrEmail", "domainCodes", "status", "isActive"
        );
        List<CandidateDto> candidates = candidateService.getAllCandidates();
        List<List<Object>> rows = candidates.stream()
                .map(c -> {
                    String deptCode = "";
                    if (c.departmentId() != null) {
                        deptCode = departmentRepository.findById(c.departmentId())
                                .map(Department::getCode)
                                .orElse("");
                    }
                    String domainCodes = c.domains() == null ? "" : ExcelHelper.joinList(
                            c.domains().stream().map(DomainDto::code).filter(Objects::nonNull).toList()
                    );
                    String hrEmail = "";
                    if (c.coordinatedHrId() != null) {
                        hrEmail = userRepository.findById(c.coordinatedHrId())
                                .map(User::getEmail)
                                .orElse("");
                    }
                    return List.<Object>of(
                            c.name(),
                            c.email(),
                            nullToEmpty(c.phone()),
                            nullToEmpty(deptCode),
                            nullToEmpty(c.departmentName()),
                            nullToEmpty(c.targetDesignationName()),
                            nullToEmpty(c.location()),
                            c.yearsOfExperience() == null ? "" : c.yearsOfExperience(),
                            nullToEmpty(c.jobReferenceCode()),
                            nullToEmpty(c.resourceRequestNumber()),
                            nullToEmpty(c.notes()),
                            hrEmail,
                            domainCodes,
                            nullToEmpty(c.status()),
                            c.isActive()
                    );
                })
                .toList();
        return ExcelHelper.writeSheet("Candidates", headers, rows, Map.of(
                "departmentCode", departmentCodes(),
                "departmentName", departmentNames(),
                "designationName", designationNames(),
                "coordinatedHrEmail", coordinatorEmails(),
                "domainCodes", domainCodes(),
                "isActive", booleanChoices()
        ));
    }

    @Transactional
    public ExcelImportResultDto importCandidates(MultipartFile file, User changedBy) {
        ExcelHelper.validateExcelFile(file);
        ExcelImportResultDto.Builder result = ExcelImportResultDto.builder();
        try {
            List<Map<String, String>> rows = ExcelHelper.readRows(file.getInputStream());
            int rowNum = 1;
            for (Map<String, String> row : rows) {
                rowNum++;
                try {
                    String name = ExcelHelper.get(row, "name");
                    String email = ExcelHelper.get(row, "email");
                    if (name.isBlank()) {
                        throw new IllegalArgumentException("name is required");
                    }
                    if (email.isBlank()) {
                        throw new IllegalArgumentException("email is required");
                    }
                    String hrEmail = ExcelHelper.get(row, "coordinatedHrEmail", "coordinated_hr_email", "coordinatorEmail");
                    if (hrEmail.isBlank()) {
                        throw new IllegalArgumentException("coordinatedHrEmail is required");
                    }
                    User hr = userRepository.findByEmail(hrEmail.trim().toLowerCase(Locale.ROOT))
                            .or(() -> userRepository.findByEmail(hrEmail.trim()))
                            .orElseThrow(() -> new IllegalArgumentException("Coordinator not found: " + hrEmail));

                    Department department = null;
                    String deptCode = ExcelHelper.get(row, "departmentCode", "department_code");
                    String deptName = ExcelHelper.get(row, "departmentName", "department_name", "department");
                    if (!deptCode.isBlank() || !deptName.isBlank()) {
                        department = resolveDepartment(deptCode, deptName);
                    }

                    Long designationId = null;
                    String designationName = ExcelHelper.get(row, "designationName", "designation_name", "designation", "targetDesignation");
                    if (!designationName.isBlank()) {
                        Designation designation = resolveImportedDesignation(department, designationName);
                        designationId = designation.getId();
                        if (department == null) {
                            department = designation.getDepartment();
                        }
                    }

                    List<Long> domainIds = new ArrayList<>();
                    for (String domainRef : ExcelHelper.splitList(ExcelHelper.get(row, "domainCodes", "domain_codes", "domains"))) {
                        Domain domain = domainRepository.findByCodeIgnoreCase(domainRef)
                                .or(() -> domainRepository.findByNameIgnoreCase(domainRef))
                                .orElseThrow(() -> new IllegalArgumentException("Domain not found: " + domainRef));
                        domainIds.add(domain.getId());
                    }

                    candidateService.createCandidate(new CreateCandidateDto(
                            name,
                            email,
                            blankToNull(ExcelHelper.get(row, "phone")),
                            department != null ? department.getId() : null,
                            designationId,
                            null,
                            null,
                            null,
                            blankToNull(ExcelHelper.get(row, "jobReferenceCode", "job_reference_code")),
                            blankToNull(ExcelHelper.get(row, "resourceRequestNumber", "resource_request_number")),
                            blankToNull(ExcelHelper.get(row, "location")),
                            blankToNull(ExcelHelper.get(row, "notes")),
                            ExcelHelper.getInteger(row, "yearsOfExperience", "years_of_experience", "experience"),
                            hr.getId(),
                            domainIds.isEmpty() ? null : domainIds
                    ), changedBy);
                    result.created();
                } catch (Exception e) {
                    String msg = e.getMessage() == null ? "failed" : e.getMessage();
                    String lower = msg.toLowerCase(Locale.ROOT);
                    if (lower.contains("already exists") || lower.contains("does not belong")) {
                        result.skipped("Row " + rowNum + ": " + msg);
                    } else {
                        result.failed("Row " + rowNum + ": " + msg);
                    }
                }
            }
        } catch (Exception e) {
            result.failed(e.getMessage());
        }
        return result.build();
    }

    // ── Question categories ──────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public byte[] exportQuestionCategories() {
        List<String> headers = List.of("code", "label", "displayOrder", "isActive", "isSystem");
        List<List<Object>> rows = questionCategoryService.getActiveCategories(false).stream()
                .map(c -> List.<Object>of(
                        nullToEmpty(c.code()),
                        c.label(),
                        c.displayOrder(),
                        c.isActive(),
                        c.isSystem()
                ))
                .toList();
        return ExcelHelper.writeSheet("QuestionCategories", headers, rows);
    }

    @Transactional
    public ExcelImportResultDto importQuestionCategories(MultipartFile file) {
        ExcelHelper.validateExcelFile(file);
        ExcelImportResultDto.Builder result = ExcelImportResultDto.builder();
        try {
            List<Map<String, String>> rows = ExcelHelper.readRows(file.getInputStream());
            int rowNum = 1;
            for (Map<String, String> row : rows) {
                rowNum++;
                try {
                    String label = ExcelHelper.get(row, "label", "name");
                    if (label.isBlank()) {
                        throw new IllegalArgumentException("label is required");
                    }
                    questionCategoryService.createCategory(new CreateQuestionCategoryDto(
                            blankToNull(ExcelHelper.get(row, "code")),
                            label,
                            ExcelHelper.getInteger(row, "displayOrder", "display_order", "order")
                    ));
                    result.created();
                } catch (Exception e) {
                    String msg = e.getMessage() == null ? "failed" : e.getMessage();
                    if (msg.toLowerCase(Locale.ROOT).contains("already exists")
                            || msg.toLowerCase(Locale.ROOT).contains("system")) {
                        result.skipped("Row " + rowNum + ": " + msg);
                    } else {
                        result.failed("Row " + rowNum + ": " + msg);
                    }
                }
            }
        } catch (Exception e) {
            result.failed(e.getMessage());
        }
        return result.build();
    }

    // ── Obligatory questions ─────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public byte[] exportObligatoryQuestions() {
        List<String> headers = List.of(
                "order", "label", "type", "required", "commentsEnabled", "placeholder", "helpText", "options"
        );
        List<List<Object>> rows = feedbackService.listObligatoryQuestions().stream()
                .map(q -> List.<Object>of(
                        q.order(),
                        q.label(),
                        q.type(),
                        q.required(),
                        q.commentsEnabled(),
                        nullToEmpty(q.placeholder()),
                        nullToEmpty(q.helpText()),
                        optionsToCell(q.options())
                ))
                .toList();
        return ExcelHelper.writeSheet("ObligatoryQuestions", headers, rows, Map.of(
                "type", List.of("text", "multiline", "dropdown"),
                "required", booleanChoices(),
                "commentsEnabled", booleanChoices()
        ));
    }

    @Transactional
    public ExcelImportResultDto importObligatoryQuestions(MultipartFile file) {
        ExcelHelper.validateExcelFile(file);
        ExcelImportResultDto.Builder result = ExcelImportResultDto.builder();
        try {
            List<Map<String, String>> rows = ExcelHelper.readRows(file.getInputStream());
            int rowNum = 1;
            for (Map<String, String> row : rows) {
                rowNum++;
                try {
                    String label = ExcelHelper.get(row, "label", "question");
                    String type = ExcelHelper.get(row, "type");
                    if (label.isBlank()) {
                        throw new IllegalArgumentException("label is required");
                    }
                    if (type.isBlank()) {
                        type = "text";
                    }
                    Boolean required = ExcelHelper.getBoolean(row, "required");
                    Boolean commentsEnabled = ExcelHelper.getBoolean(row, "commentsEnabled", "comments_enabled");
                    List<FeedbackOptionDto> options = parseOptions(ExcelHelper.get(row, "options"));
                    feedbackService.createObligatoryQuestion(new CreateFeedbackQuestionDto(
                            ExcelHelper.getInteger(row, "order", "displayOrder", "display_order"),
                            label,
                            null,
                            null,
                            type,
                            required == null || required,
                            commentsEnabled != null && commentsEnabled,
                            blankToNull(ExcelHelper.get(row, "placeholder")),
                            blankToNull(ExcelHelper.get(row, "helpText", "help_text")),
                            options
                    ));
                    result.created();
                } catch (Exception e) {
                    result.failed("Row " + rowNum + ": " + e.getMessage());
                }
            }
        } catch (Exception e) {
            result.failed(e.getMessage());
        }
        return result.build();
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private byte[] exportCatalogTypes(String sheetName, List<CatalogTypeDto> types) {
        List<String> headers = List.of("code", "label", "displayOrder", "active");
        List<List<Object>> rows = types.stream()
                .map(t -> List.<Object>of(t.code(), t.label(), t.displayOrder(), t.active()))
                .toList();
        return ExcelHelper.writeSheet(sheetName, headers, rows);
    }

    private ExcelImportResultDto importCatalogTypes(
            MultipartFile file,
            java.util.function.Function<CreateCatalogTypeDto, CatalogTypeDto> creator
    ) {
        ExcelHelper.validateExcelFile(file);
        ExcelImportResultDto.Builder result = ExcelImportResultDto.builder();
        try {
            List<Map<String, String>> rows = ExcelHelper.readRows(file.getInputStream());
            int rowNum = 1;
            for (Map<String, String> row : rows) {
                rowNum++;
                try {
                    String label = ExcelHelper.get(row, "label", "name");
                    if (label.isBlank()) {
                        throw new IllegalArgumentException("label is required");
                    }
                    creator.apply(new CreateCatalogTypeDto(
                            blankToNull(ExcelHelper.get(row, "code")),
                            label,
                            ExcelHelper.getInteger(row, "displayOrder", "display_order", "order")
                    ));
                    result.created();
                } catch (Exception e) {
                    String msg = e.getMessage() == null ? "failed" : e.getMessage();
                    if (msg.toLowerCase(Locale.ROOT).contains("already exists")) {
                        result.skipped("Row " + rowNum + ": " + msg);
                    } else {
                        result.failed("Row " + rowNum + ": " + msg);
                    }
                }
            }
        } catch (Exception e) {
            result.failed(e.getMessage());
        }
        return result.build();
    }

    private Department resolveDepartment(String code, String name) {
        if (code != null && !code.isBlank()) {
            return departmentRepository.findByCodeIgnoreCase(code.trim())
                    .orElseThrow(() -> new IllegalArgumentException("Department not found for code: " + code));
        }
        if (name != null && !name.isBlank()) {
            Department department = departmentRepository.findByNameIgnoreCase(name.trim());
            if (department == null) {
                throw new IllegalArgumentException("Department not found: " + name);
            }
            return department;
        }
        throw new IllegalArgumentException("departmentCode or departmentName is required");
    }

    private Tier resolveTier(Long departmentId, String tierName, Integer tierOrder) {
        List<Tier> tiers = tierRepository.findByDepartmentIdAndIsActiveTrueOrderByTierOrderAsc(departmentId);
        if (tierName != null && !tierName.isBlank()) {
            return tiers.stream()
                    .filter(t -> t.getName().equalsIgnoreCase(tierName.trim()))
                    .findFirst()
                    .orElseThrow(() -> new IllegalArgumentException("Tier not found: " + tierName));
        }
        if (tierOrder != null) {
            return tiers.stream()
                    .filter(t -> Objects.equals(t.getTierOrder(), tierOrder))
                    .findFirst()
                    .orElseThrow(() -> new IllegalArgumentException("Tier not found for order: " + tierOrder));
        }
        throw new IllegalArgumentException("tierName or tierOrder is required");
    }

    private List<String> departmentCodes() {
        return ExcelHelper.uniqueNonBlank(departmentRepository.findAll().stream()
                .map(Department::getCode)
                .toList());
    }

    private List<String> departmentNames() {
        return ExcelHelper.uniqueNonBlank(departmentRepository.findAll().stream()
                .map(Department::getName)
                .toList());
    }

    private List<String> tierNames() {
        return ExcelHelper.uniqueNonBlank(tierRepository.findByIsActiveTrueOrderByTierOrderAsc().stream()
                .map(Tier::getName)
                .toList());
    }

    private List<String> designationNames() {
        return ExcelHelper.uniqueNonBlank(designationRepository.findByIsActiveTrue().stream()
                .map(d -> {
                    String deptName = d.getDepartment() != null ? d.getDepartment().getName() : "";
                    return deptName.isBlank() ? d.getName() : deptName + " | " + d.getName();
                })
                .toList());
    }

    private Designation resolveImportedDesignation(Department department, String rawValue) {
        String raw = ExcelHelper.normalizeLookup(rawValue);
        String nameOnly = raw;
        String deptHint = null;
        int separator = raw.indexOf('|');
        if (separator > 0) {
            deptHint = ExcelHelper.normalizeLookup(raw.substring(0, separator));
            nameOnly = ExcelHelper.normalizeLookup(raw.substring(separator + 1));
        }
        if (nameOnly.isBlank()) {
            throw new IllegalArgumentException("designationName is required");
        }

        List<Designation> matches = designationRepository.findByIsActiveTrue().stream()
                .filter(d -> ExcelHelper.lookupEquals(d.getName(), nameOnly))
                .toList();
        if (matches.isEmpty()) {
            boolean inactiveExists = designationRepository.findAll().stream()
                    .anyMatch(d -> ExcelHelper.lookupEquals(d.getName(), nameOnly));
            throw new IllegalArgumentException(inactiveExists
                    ? "Designation is inactive: " + nameOnly
                    : "Designation not found: " + nameOnly);
        }

        if (department != null) {
            List<Designation> inDepartment = matches.stream()
                    .filter(d -> d.getDepartment() != null && d.getDepartment().getId().equals(department.getId()))
                    .toList();
            if (inDepartment.size() == 1) {
                return inDepartment.get(0);
            }
            String actualDepartments = matches.stream()
                    .map(d -> d.getDepartment() != null ? d.getDepartment().getName() : "Unknown")
                    .distinct()
                    .collect(Collectors.joining(", "));
            throw new IllegalArgumentException(
                    "Designation '" + nameOnly + "' does not belong to department '" + department.getName()
                            + "'. It belongs to: " + actualDepartments
            );
        }

        if (deptHint != null && !deptHint.isBlank()) {
            List<Designation> hinted = matches.stream()
                    .filter(d -> d.getDepartment() != null
                            && (ExcelHelper.lookupEquals(d.getDepartment().getName(), deptHint)
                            || ExcelHelper.lookupEquals(d.getDepartment().getCode(), deptHint)))
                    .toList();
            if (hinted.size() == 1) {
                return hinted.get(0);
            }
            if (hinted.isEmpty()) {
                throw new IllegalArgumentException(
                        "Designation '" + nameOnly + "' does not belong to department '" + deptHint + "'"
                );
            }
            matches = hinted;
        }

        if (matches.size() == 1) {
            return matches.get(0);
        }

        String departments = matches.stream()
                .map(d -> d.getDepartment() != null ? d.getDepartment().getName() : "Unknown")
                .distinct()
                .collect(Collectors.joining(", "));
        throw new IllegalArgumentException(
                "Designation '" + nameOnly + "' exists in multiple departments (" + departments
                        + "). Pick it as 'Department | " + nameOnly + "'."
        );
    }

    private List<String> domainCodes() {
        return ExcelHelper.uniqueNonBlank(domainRepository.findByIsActiveTrueOrderByNameAsc().stream()
                .map(Domain::getCode)
                .toList());
    }

    private List<String> coordinatorEmails() {
        List<String> emails = new ArrayList<>();
        emails.addAll(userRepository.findByRolesContaining(Role.HR).stream()
                .filter(User::isActive)
                .map(User::getEmail)
                .toList());
        emails.addAll(userRepository.findByRolesContaining(Role.ADMIN).stream()
                .filter(User::isActive)
                .map(User::getEmail)
                .toList());
        return ExcelHelper.uniqueNonBlank(emails);
    }

    private List<String> masterStepKeys() {
        return ExcelHelper.uniqueNonBlank(
                masterStepRepository.findAllByIsActiveTrueAndIsVisibleTrueOrderByStepOrderAsc().stream()
                        .map(MasterStep::getStatusKey)
                        .toList()
        );
    }

    private static List<String> booleanChoices() {
        return List.of("TRUE", "FALSE");
    }

    private static String optionsToCell(List<FeedbackOptionDto> options) {
        if (options == null || options.isEmpty()) {
            return "";
        }
        return ExcelHelper.joinList(options.stream()
                .map(o -> o.label() != null ? o.label() : String.valueOf(o.value()))
                .toList());
    }

    private static List<FeedbackOptionDto> parseOptions(String raw) {
        List<String> parts = ExcelHelper.splitList(raw);
        if (parts.isEmpty()) {
            return List.of();
        }
        List<FeedbackOptionDto> options = new ArrayList<>();
        for (String part : parts) {
            options.add(new FeedbackOptionDto(part, part));
        }
        return options;
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
