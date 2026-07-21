package com.nemal.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.nemal.dto.*;
import com.nemal.entity.*;
import com.nemal.enums.MasterStatus;
import com.nemal.enums.PipelineAuditActionType;
import com.nemal.enums.Role;
import com.nemal.repository.*;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.util.Comparator;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class FeedbackService {

    private static final Logger logger = LoggerFactory.getLogger(FeedbackService.class);

    private final FeedbackFormRepository feedbackFormRepository;
    private final FeedbackQuestionRepository feedbackQuestionRepository;
    private final FeedbackResponseRepository feedbackResponseRepository;
    private final InterviewScheduleRepository interviewScheduleRepository;
    private final InterviewPanelRepository interviewPanelRepository;
    private final InterviewRequestRepository interviewRequestRepository;
    private final QuestionCategoryService questionCategoryService;
    private final CandidatePipelineAuditService candidatePipelineAuditService;
    private final NotificationService notificationService;
    private final InterviewTypeService interviewTypeService;
    private final ObjectMapper objectMapper;

    @Transactional(readOnly = true)
    public FeedbackFormDto getActiveFeedbackForm() {
        FeedbackForm form = feedbackFormRepository.findFirstByIsActiveTrueOrderByIdDesc()
                .orElseThrow(() -> new RuntimeException("No active feedback form found"));

        List<FeedbackQuestionDto> questions = feedbackQuestionRepository
                .findByFormIdAndIsObligatoryFalseAndIsActiveTrueOrderByDisplayOrderAsc(form.getId())
                .stream()
                .map(this::toQuestionDto)
                .toList();

        List<FeedbackQuestionDto> obligatoryQuestions = feedbackQuestionRepository
                .findByIsObligatoryTrueAndIsActiveTrueOrderByDisplayOrderAsc()
                .stream()
                .map(this::toQuestionDto)
                .toList();

        return new FeedbackFormDto(
            form.getId(),
            form.getName(),
            form.getDescription(),
            form.isActive(),
            form.getVersionNumber(),
            new FeedbackScopesDto(
                parseLongList(form.getDepartmentIdsJson()),
                parseLongList(form.getDesignationIdsJson()),
                parseStringList(form.getInterviewTypesJson())
            ),
            questions,
            obligatoryQuestions
        );
    }

    @Transactional
    public FeedbackFormDto createForm(CreateFeedbackFormDto dto) {
        validateFormScopes(dto.scopes());
        FeedbackForm form = FeedbackForm.builder()
            .name(dto.name())
            .description(dto.description())
            .departmentIdsJson(writeJsonNode(dto.scopes() == null ? List.of() : dto.scopes().departmentIds()))
            .designationIdsJson(writeJsonNode(dto.scopes() == null ? List.of() : dto.scopes().designationIds()))
            .interviewTypesJson(writeJsonNode(normalizeInterviewTypes(dto.scopes())))
            .seriesKey(UUID.randomUUID().toString())
            .versionNumber(1)
            .isActive(true)
            .build();

        FeedbackForm saved = feedbackFormRepository.save(form);

        if (dto.questions() != null) {
            int order = 1;
            for (CreateFeedbackQuestionDto qdto : dto.questions()) {
                FeedbackQuestion q = buildQuestionFromDto(saved, qdto, qdto.order() == null ? order : qdto.order());
                feedbackQuestionRepository.save(q);
                order++;
            }
        }

        return getFormById(saved.getId());
    }

    @Transactional
    public FeedbackFormDto updateForm(Long formId, CreateFeedbackFormDto dto) {
        validateFormScopes(dto.scopes());
        FeedbackForm currentForm = feedbackFormRepository.findById(formId)
                .orElseThrow(() -> new RuntimeException("Feedback form not found: " + formId));
        // If the update contains no questions, treat it as a scope/name/description update
        // and modify the existing form in-place without creating a new version.
        if (dto.questions() == null || dto.questions().isEmpty()) {
            currentForm.setName(dto.name());
            currentForm.setDescription(dto.description());
            currentForm.setDepartmentIdsJson(writeJsonNode(dto.scopes() == null ? List.of() : dto.scopes().departmentIds()));
            currentForm.setDesignationIdsJson(writeJsonNode(dto.scopes() == null ? List.of() : dto.scopes().designationIds()));
            currentForm.setInterviewTypesJson(writeJsonNode(normalizeInterviewTypes(dto.scopes())));
            FeedbackForm updated = feedbackFormRepository.save(currentForm);
            return getFormById(updated.getId());
        }

        // If questions are provided, compare them with the current active questions.
        // Only create a new version when questions were added, removed, or edited.
        List<FeedbackQuestionDto> existingQuestions = feedbackQuestionRepository
                .findByFormIdAndIsObligatoryFalseAndIsActiveTrueOrderByDisplayOrderAsc(currentForm.getId())
                .stream()
                .map(this::toQuestionDto)
                .toList();

        // Normalize existing questions to a list of maps (exclude database ids)
        List<Map<String, Object>> existingNormalized = new java.util.ArrayList<>();
        for (FeedbackQuestionDto q : existingQuestions) {
            Map<String, Object> m = new java.util.HashMap<>();
            m.put("order", q.order());
            m.put("label", q.label());
            m.put("categoryId", q.categoryId());
            m.put("type", q.type());
            m.put("required", q.required());
            m.put("commentsEnabled", q.commentsEnabled());
            m.put("placeholder", q.placeholder());
            m.put("helpText", q.helpText());
            // Normalize options to a list of {value,label} objects for reliable comparison
            List<Object> optList = new java.util.ArrayList<>();
            if (q.options() != null) {
                for (Object opt : q.options()) {
                    if (opt == null) continue;
                    if (opt instanceof Map) {
                        optList.add(opt);
                        continue;
                    }
                    // opt may be a FeedbackOptionDto-like map or a simple string
                    Map<String, Object> optMap = new java.util.HashMap<>();
                    optMap.put("value", opt);
                    optMap.put("label", opt.toString());
                    optList.add(optMap);
                }
            }
            m.put("options", optList);
            existingNormalized.add(m);
        }

        var incomingNode = objectMapper.valueToTree(dto.questions());
        var existingNode = objectMapper.valueToTree(existingNormalized);

        if (incomingNode.equals(existingNode)) {
            // Questions unchanged — update metadata in-place and return current form
            currentForm.setName(dto.name());
            currentForm.setDescription(dto.description());
            currentForm.setDepartmentIdsJson(writeJsonNode(dto.scopes() == null ? List.of() : dto.scopes().departmentIds()));
            currentForm.setDesignationIdsJson(writeJsonNode(dto.scopes() == null ? List.of() : dto.scopes().designationIds()));
            currentForm.setInterviewTypesJson(writeJsonNode(normalizeInterviewTypes(dto.scopes())));
            FeedbackForm updated = feedbackFormRepository.save(currentForm);
            return getFormById(updated.getId());
        }

        // Otherwise questions are present and different — create a new version (immutable history for questions)
        FeedbackForm form = FeedbackForm.builder()
            .name(dto.name())
            .description(dto.description())
            .departmentIdsJson(writeJsonNode(dto.scopes() == null ? List.of() : dto.scopes().departmentIds()))
            .designationIdsJson(writeJsonNode(dto.scopes() == null ? List.of() : dto.scopes().designationIds()))
            .interviewTypesJson(writeJsonNode(normalizeInterviewTypes(dto.scopes())))
            .seriesKey(currentForm.getSeriesKey())
            .versionNumber((currentForm.getVersionNumber() == null ? 1 : currentForm.getVersionNumber()) + 1)
            .isActive(true)
            .build();

        FeedbackForm savedForm = feedbackFormRepository.save(form);

        currentForm.setActive(false);
        feedbackFormRepository.save(currentForm);

        if (dto.questions() != null) {
            int order = 1;
            for (CreateFeedbackQuestionDto qdto : dto.questions()) {
                FeedbackQuestion q = buildQuestionFromDto(savedForm, qdto, qdto.order() == null ? order : qdto.order());
                feedbackQuestionRepository.save(q);
                order++;
            }
        }

        return getFormById(savedForm.getId());
    }

    @Transactional
    public void deleteForm(Long formId) {
        FeedbackForm form = feedbackFormRepository.findById(formId)
                .orElseThrow(() -> new RuntimeException("Feedback form not found: " + formId));

        if (feedbackResponseRepository.existsByFormId(formId)) {
            throw new RuntimeException("Can't delete this feedback form because it is already used in feedback responses.");
        }

        feedbackQuestionRepository.deleteByFormId(formId);
        feedbackFormRepository.delete(form);
    }

    @Transactional
    public FeedbackQuestionDto createQuestion(Long formId, CreateFeedbackQuestionDto dto) {
        FeedbackForm form = feedbackFormRepository.findById(formId)
                .orElseThrow(() -> new RuntimeException("Feedback form not found: " + formId));

        FeedbackQuestion q = buildQuestionFromDto(form, dto, dto.order() == null ? 1 : dto.order());

        FeedbackQuestion saved = feedbackQuestionRepository.save(q);
        return toQuestionDto(saved);
    }

    @Transactional
    public FeedbackQuestionDto updateQuestion(Long questionId, CreateFeedbackQuestionDto dto) {
        FeedbackQuestion q = feedbackQuestionRepository.findById(questionId)
                .orElseThrow(() -> new RuntimeException("Feedback question not found: " + questionId));

        q.setDisplayOrder(dto.order() == null ? q.getDisplayOrder() : dto.order());
        q.setLabel(dto.label());
        q.setCategory(questionCategoryService.resolveCategory(dto.categoryId(), dto.category()));
        q.setType(dto.type());
        q.setRequired(dto.required());
        q.setCommentsEnabled(dto.commentsEnabled());
        q.setPlaceholder(dto.placeholder());
        q.setHelpText(dto.helpText());
        q.setOptionsJson(writeJsonNode(dto.options() == null ? List.of() : dto.options()));

        FeedbackQuestion saved = feedbackQuestionRepository.save(q);
        return toQuestionDto(saved);
    }

    @Transactional
    public void deleteQuestion(Long questionId) {
        FeedbackQuestion q = feedbackQuestionRepository.findById(questionId)
                .orElseThrow(() -> new RuntimeException("Feedback question not found: " + questionId));
        q.setActive(false);
        feedbackQuestionRepository.save(q);
    }

    @Transactional(readOnly = true)
    public List<FeedbackQuestionDto> listObligatoryQuestions() {
        return feedbackQuestionRepository.findByIsObligatoryTrueAndIsActiveTrueOrderByDisplayOrderAsc()
                .stream()
                .map(this::toQuestionDto)
                .toList();
    }

    @Transactional
    public FeedbackQuestionDto createObligatoryQuestion(CreateFeedbackQuestionDto dto) {
        int displayOrder = dto.order() != null
                ? dto.order()
                : nextObligatoryDisplayOrder();

        FeedbackQuestion question = FeedbackQuestion.builder()
                .form(null)
                .displayOrder(displayOrder)
                .label(dto.label())
                .category(questionCategoryService.requireObligatoryCategory())
                .type(dto.type())
                .required(dto.required())
                .commentsEnabled(dto.commentsEnabled())
                .placeholder(dto.placeholder())
                .helpText(dto.helpText())
                .optionsJson(writeJsonNode(dto.options() == null ? List.of() : dto.options()))
                .isActive(true)
                .isObligatory(true)
                .build();

        return toQuestionDto(feedbackQuestionRepository.save(question));
    }

    @Transactional
    public FeedbackQuestionDto updateObligatoryQuestion(Long questionId, CreateFeedbackQuestionDto dto) {
        FeedbackQuestion question = requireActiveObligatoryQuestion(questionId);

        question.setDisplayOrder(dto.order() == null ? question.getDisplayOrder() : dto.order());
        question.setLabel(dto.label());
        question.setType(dto.type());
        question.setRequired(dto.required());
        question.setCommentsEnabled(dto.commentsEnabled());
        question.setPlaceholder(dto.placeholder());
        question.setHelpText(dto.helpText());
        question.setOptionsJson(writeJsonNode(dto.options() == null ? List.of() : dto.options()));

        return toQuestionDto(feedbackQuestionRepository.save(question));
    }

    @Transactional
    public void deleteObligatoryQuestion(Long questionId) {
        requireActiveObligatoryQuestion(questionId);
        deleteQuestion(questionId);
    }

    private FeedbackQuestion requireActiveObligatoryQuestion(Long questionId) {
        FeedbackQuestion question = feedbackQuestionRepository.findById(questionId)
                .orElseThrow(() -> new RuntimeException("Feedback question not found: " + questionId));
        if (!question.isObligatory() || !question.isActive()) {
            throw new RuntimeException("Question is not an active obligatory question: " + questionId);
        }
        return question;
    }

    private int nextObligatoryDisplayOrder() {
        return feedbackQuestionRepository.findByIsObligatoryTrueAndIsActiveTrueOrderByDisplayOrderAsc()
                .stream()
                .mapToInt(FeedbackQuestion::getDisplayOrder)
                .max()
                .orElse(0) + 1;
    }

    @Transactional
    public FeedbackResponseDto submitFeedback(CreateFeedbackResponseDto dto, User interviewer) {
        // Log incoming request DTO for debugging (serialize to JSON)
        try {
            String dtoJson = objectMapper.writeValueAsString(dto);
            logger.info("submitFeedback request DTO: {}", dtoJson);
        } catch (Exception e) {
            logger.warn("Failed to serialize submitFeedback DTO for logging: {}", e.getMessage());
        }
        if (dto.interviewScheduleId() == null) {
            throw new RuntimeException("interviewScheduleId is required");
        }

        InterviewSchedule schedule = interviewScheduleRepository.findById(dto.interviewScheduleId())
                .orElseThrow(() -> new RuntimeException("Interview schedule not found: " + dto.interviewScheduleId()));

        FeedbackForm form;
        if (dto.feedbackFormId() != null) {
            form = feedbackFormRepository.findById(dto.feedbackFormId())
                .orElseThrow(() -> new RuntimeException("Feedback form not found: " + dto.feedbackFormId()));
            if (!form.isActive()) {
            throw new RuntimeException("Selected feedback form is inactive: " + dto.feedbackFormId());
            }
        } else {
            form = feedbackFormRepository.findFirstByIsActiveTrueOrderByIdDesc()
                .orElseThrow(() -> new RuntimeException("No active feedback form found"));
        }

        LocalDateTime submittedAt = parseSubmittedAt(dto.submittedAt());
        var responsesJson = writeJsonNode(dto.responses() != null ? dto.responses() : Collections.emptyMap());

        FeedbackResponse response = feedbackResponseRepository
                .findByInterviewScheduleIdAndInterviewerId(schedule.getId(), interviewer.getId())
                .orElseGet(FeedbackResponse::new);

        boolean isNewSubmission = response.getId() == null;

        response.setInterviewSchedule(schedule);
        response.setInterviewer(interviewer);
        response.setForm(form);
        response.setResponsesJson(responsesJson);
        response.setSubmittedAt(submittedAt);

        FeedbackResponse saved = feedbackResponseRepository.save(response);

        // Log saved entity and JSON responses
        try {
            String respJson = saved.getResponsesJson() != null ? saved.getResponsesJson().toString() : "{}";
            logger.info("submitFeedback saved id={}, interviewScheduleId={}, interviewerId={}, responses={}", saved.getId(), saved.getInterviewSchedule().getId(), saved.getInterviewer() != null ? saved.getInterviewer().getId() : null, respJson);
        } catch (Exception e) {
            logger.warn("Failed to log saved FeedbackResponse: {}", e.getMessage());
        }

        if (isNewSubmission) {
            recordFeedbackSubmittedAudit(schedule, form, interviewer);
            InterviewRequest request = schedule.getRequest();
            if (request != null && request.getCandidate() != null) {
                String interviewType = schedule.getInterviewType() != null
                        ? schedule.getInterviewType()
                        : InterviewTypeService.DEFAULT_CODE;
                notificationService.sendFeedbackSubmittedNotification(
                        request.getCandidate(),
                        interviewer,
                        interviewType
                );
            }
        }

        return toResponseDto(saved);
    }

    private void recordFeedbackSubmittedAudit(InterviewSchedule schedule,
                                              FeedbackForm form,
                                              User interviewer) {
        InterviewRequest request = schedule.getRequest();
        if (request == null || request.getCandidate() == null) {
            return;
        }

        Candidate candidate = request.getCandidate();
        String interviewType = schedule.getInterviewType() != null
                ? schedule.getInterviewType()
                : InterviewTypeService.DEFAULT_CODE;
        String stageStatusKey = interviewTypeService.roundStatusKey(interviewType);
        String notes = interviewType + " feedback submitted";
        if (form != null && form.getName() != null && !form.getName().isBlank()) {
            notes = form.getName() + " · " + notes;
        }

        String previousStatusKey = candidate.getMasterStep() != null
                ? candidate.getMasterStep().getStatusKey()
                : null;
        candidatePipelineAuditService.recordStatusChange(
                candidate.getId(),
                stageStatusKey,
                previousStatusKey,
                PipelineAuditActionType.FEEDBACK_SUBMITTED,
                interviewer,
                notes);
    }

    @Transactional(readOnly = true)
    public Optional<FeedbackResponseDto> findFeedbackForInterview(Long interviewScheduleId) {
        Optional<FeedbackResponseDto> direct = feedbackResponseRepository.findByInterviewScheduleId(interviewScheduleId)
                .map(this::toResponseDto);
        if (direct.isPresent()) {
            return direct;
        }
        return findPanelFeedbackForSchedule(interviewScheduleId);
    }

    @Transactional(readOnly = true)
    public FeedbackResponseDto getFeedbackForInterview(Long interviewScheduleId) {
        return findFeedbackForInterview(interviewScheduleId)
                .orElseThrow(() -> new RuntimeException("Feedback not found for interview schedule: " + interviewScheduleId));
    }

    @Transactional(readOnly = true)
    public FeedbackResponseDto getFeedbackForInterview(Long interviewScheduleId, User user) {
        InterviewSchedule schedule = interviewScheduleRepository.findById(interviewScheduleId)
                .orElseThrow(() -> new RuntimeException("Interview schedule not found: " + interviewScheduleId));

        boolean isHr = user.getRoles().contains(Role.HR);
        boolean isAssignedInterviewer = schedule.getInterviewer() != null
                && schedule.getInterviewer().getId().equals(user.getId());
        boolean isPanelPeer = !isAssignedInterviewer && isPanelInterviewerForSchedule(user, schedule);

        if (!isHr && !isAssignedInterviewer && !isPanelPeer) {
            throw new RuntimeException("You are not allowed to view feedback for this interview");
        }

        return getFeedbackForInterview(interviewScheduleId);
    }

    @Transactional(readOnly = true)
    public FeedbackInterviewViewDto getFeedbackViewForInterview(Long interviewScheduleId, User user) {
        FeedbackResponseDto response = getFeedbackForInterview(interviewScheduleId, user);
        FeedbackFormDto form = null;
        if (response.feedbackFormId() != null) {
            form = getFormById(response.feedbackFormId());
        }
        return new FeedbackInterviewViewDto(response, form);
    }

    private Optional<FeedbackResponseDto> findPanelFeedbackForSchedule(Long interviewScheduleId) {
        Optional<FeedbackResponseDto> peerFeedback = feedbackResponseRepository
                .findPanelFeedbackForPeerSchedule(interviewScheduleId)
                .stream()
                .findFirst()
                .map(this::toResponseDto);
        if (peerFeedback.isPresent()) {
            return peerFeedback;
        }

        Long panelId = resolvePanelIdForSchedule(interviewScheduleId);
        if (panelId == null) {
            return Optional.empty();
        }

        Optional<FeedbackResponseDto> panelFeedback = feedbackResponseRepository
                .findByPanelIdOrderBySubmittedAtDesc(panelId)
                .stream()
                .findFirst()
                .map(this::toResponseDto);
        if (panelFeedback.isPresent()) {
            return panelFeedback;
        }

        return interviewScheduleRepository.findByPanelId(panelId).stream()
                .map(InterviewSchedule::getId)
                .map(feedbackResponseRepository::findByInterviewScheduleId)
                .filter(Optional::isPresent)
                .map(Optional::get)
                .map(this::toResponseDto)
                .findFirst();
    }

    private Long resolvePanelIdForSchedule(Long interviewScheduleId) {
        Optional<Long> fromRequest = interviewRequestRepository
                .findPanelIdByInterviewScheduleId(interviewScheduleId);
        if (fromRequest.isPresent()) {
            return fromRequest.get();
        }

        Optional<Long> fromSchedule = interviewScheduleRepository.findByIdWithRequestAndPanel(interviewScheduleId)
                .map(InterviewSchedule::getRequest)
                .filter(Objects::nonNull)
                .map(InterviewRequest::getPanel)
                .filter(Objects::nonNull)
                .map(InterviewPanel::getId);

        if (fromSchedule.isPresent()) {
            return fromSchedule.get();
        }

        return interviewRequestRepository.findByInterviewScheduleIdWithDetails(interviewScheduleId)
                .stream()
                .findFirst()
                .map(InterviewRequest::getPanel)
                .filter(Objects::nonNull)
                .map(InterviewPanel::getId)
                .orElse(null);
    }

    private boolean isPanelInterviewerForSchedule(User user, InterviewSchedule schedule) {
        Long panelId = resolvePanelIdForSchedule(schedule.getId());
        if (panelId == null) {
            return false;
        }

        return interviewRequestRepository.findByPanelIdWithDetails(panelId)
                .stream()
                .map(InterviewRequest::getAssignedInterviewer)
                .filter(Objects::nonNull)
                .anyMatch(interviewer -> interviewer.getId().equals(user.getId()));
    }

    private FeedbackQuestion buildQuestionFromDto(FeedbackForm form, CreateFeedbackQuestionDto dto, int displayOrder) {
        return FeedbackQuestion.builder()
                .form(form)
                .displayOrder(displayOrder)
                .label(dto.label())
                .category(questionCategoryService.resolveCategory(dto.categoryId(), dto.category()))
                .type(dto.type())
                .required(dto.required())
                .commentsEnabled(dto.commentsEnabled())
                .placeholder(dto.placeholder())
                .helpText(dto.helpText())
                .optionsJson(writeJsonNode(dto.options() == null ? List.of() : dto.options()))
                .isActive(true)
                .isObligatory(false)
                .build();
    }

    private FeedbackQuestionDto toQuestionDto(FeedbackQuestion question) {
        return new FeedbackQuestionDto(
            question.getId(),
            question.getDisplayOrder(),
                question.getLabel(),
                question.getCategory().getId(),
                question.getCategory().getCode(),
                question.getCategory().getLabel(),
                question.getType(),
                question.isRequired(),
                question.isCommentsEnabled(),
                question.getPlaceholder(),
                question.getHelpText(),
                parseOptions(question.getOptionsJson())
        );
    }

        @Transactional(readOnly = true)
        public FeedbackFormDto getFormById(Long formId) {
        FeedbackForm form = feedbackFormRepository.findById(formId)
            .orElseThrow(() -> new RuntimeException("Feedback form not found: " + formId));

        List<FeedbackQuestionDto> questions = feedbackQuestionRepository
            .findByFormIdAndIsObligatoryFalseAndIsActiveTrueOrderByDisplayOrderAsc(form.getId())
            .stream()
            .map(this::toQuestionDto)
            .toList();
        List<FeedbackQuestionDto> obligatoryQuestions = feedbackQuestionRepository
                .findByIsObligatoryTrueAndIsActiveTrueOrderByDisplayOrderAsc()
                .stream()
                .map(this::toQuestionDto)
                .toList();

        return new FeedbackFormDto(
            form.getId(),
            form.getName(),
            form.getDescription(),
            form.isActive(),
            form.getVersionNumber(),
            new FeedbackScopesDto(
                parseLongList(form.getDepartmentIdsJson()),
                parseLongList(form.getDesignationIdsJson()),
                parseStringList(form.getInterviewTypesJson())
            ),
            questions,
            obligatoryQuestions
        );
        }

        @Transactional(readOnly = true)
        public List<FeedbackFormDto> listAllForms() {
            return feedbackFormRepository.findAll()
            .stream()
                .sorted(Comparator.comparing(FeedbackForm::getSeriesKey).thenComparing(FeedbackForm::getVersionNumber, Comparator.nullsLast(Comparator.reverseOrder())))
            .map(f -> {
                List<FeedbackQuestionDto> questions = feedbackQuestionRepository
                    .findByFormIdAndIsObligatoryFalseAndIsActiveTrueOrderByDisplayOrderAsc(f.getId())
                    .stream()
                    .map(this::toQuestionDto)
                    .toList();

                       List<FeedbackQuestionDto> obligatoryQuestions = feedbackQuestionRepository
                .findByIsObligatoryTrueAndIsActiveTrueOrderByDisplayOrderAsc()
                .stream()
                .map(this::toQuestionDto)
                .toList();
                return new FeedbackFormDto(
                    f.getId(),
                    f.getName(),
                    f.getDescription(),
                    f.isActive(),
                    f.getVersionNumber(),
                    new FeedbackScopesDto(
                            parseLongList(f.getDepartmentIdsJson()),
                            parseLongList(f.getDesignationIdsJson()),
                            parseStringList(f.getInterviewTypesJson())
                    ),
                    questions,
                    obligatoryQuestions
                );
            })
            .toList();
        }


    @Transactional(readOnly = true)
    public List<FeedbackFormDto> listFilteredFormsByDepartmentAndDesignation(CandidateFormFilterDto dto) {
        String interviewType = normalizeInterviewTypeFilter(dto.interviewType());
        return feedbackFormRepository.findActiveFormsByDepartmentDesignationAndInterviewType(
                        dto.departmentId(),
                        dto.targetDesignationId(),
                        interviewType)
                .stream()
                .sorted(Comparator.comparing(FeedbackForm::getSeriesKey).thenComparing(FeedbackForm::getVersionNumber, Comparator.nullsLast(Comparator.reverseOrder())))
                .map(f -> {
                    List<FeedbackQuestionDto> questions = feedbackQuestionRepository
                            .findByFormIdAndIsObligatoryFalseAndIsActiveTrueOrderByDisplayOrderAsc(f.getId())
                            .stream()
                            .map(this::toQuestionDto)
                            .toList();

                    List<FeedbackQuestionDto> obligatoryQuestions = feedbackQuestionRepository
                .findByIsObligatoryTrueAndIsActiveTrueOrderByDisplayOrderAsc()
                .stream()
                .map(this::toQuestionDto)
                .toList();
                    return new FeedbackFormDto(
                            f.getId(),
                            f.getName(),
                            f.getDescription(),
                            f.isActive(),
                            f.getVersionNumber(),
                            new FeedbackScopesDto(
                                    parseLongList(f.getDepartmentIdsJson()),
                                    parseLongList(f.getDesignationIdsJson()),
                                    parseStringList(f.getInterviewTypesJson())
                            ),
                            questions,
                            obligatoryQuestions
                    );
                })
                .toList();
    }

    @Transactional
    public FeedbackFormDto setFormActive(Long formId, boolean active) {
        FeedbackForm form = feedbackFormRepository.findById(formId)
                .orElseThrow(() -> new RuntimeException("Feedback form not found: " + formId));

        if (active) {
            feedbackFormRepository.findBySeriesKey(form.getSeriesKey()).forEach(item -> {
                if (!item.getId().equals(formId) && item.isActive()) {
                    item.setActive(false);
                    feedbackFormRepository.save(item);
                }
            });
        }

        form.setActive(active);
        FeedbackForm saved = feedbackFormRepository.save(form);
        return getFormById(saved.getId());
    }

    private FeedbackResponseDto toResponseDto(FeedbackResponse response) {
        return new FeedbackResponseDto(
                response.getId(),
                response.getInterviewSchedule().getId(),
                response.getForm() != null ? response.getForm().getId() : null,
                response.getInterviewer() != null ? response.getInterviewer().getId() : null,
                parseResponseMap(response.getResponsesJson()),
                response.getSubmittedAt()
        );
    }

    private LocalDateTime parseSubmittedAt(String submittedAt) {
        if (submittedAt == null || submittedAt.isBlank()) {
            return LocalDateTime.now();
        }

        try {
            return OffsetDateTime.parse(submittedAt).toLocalDateTime();
        } catch (Exception ignored) {
        }

        try {
            return LocalDateTime.parse(submittedAt);
        } catch (Exception ignored) {
            return LocalDateTime.now();
        }
    }

    private List<Long> parseLongList(JsonNode node) {
        if (node == null || node.isNull() || !node.isArray()) {
            return List.of();
        }
        try {
            return objectMapper.convertValue(node, new TypeReference<List<Long>>() {});
        } catch (Exception e) {
            return List.of();
        }
    }

    private List<FeedbackOptionDto> parseOptions(JsonNode node) {
        if (node == null || node.isNull() || !node.isArray()) {
            return List.of();
        }
        try {
            List<FeedbackOptionDto> result = new java.util.ArrayList<>();
            for (JsonNode optionNode : node) {
                if (optionNode == null || optionNode.isNull()) {
                    continue;
                }

                if (optionNode.isTextual() || optionNode.isNumber() || optionNode.isBoolean()) {
                    String label = optionNode.asText();
                    result.add(new FeedbackOptionDto(label, label));
                    continue;
                }

                if (optionNode.isObject()) {
                    Object value = optionNode.has("value") ? objectMapper.convertValue(optionNode.get("value"), Object.class) : null;
                    String label = optionNode.has("label") ? toStringSafe(objectMapper.convertValue(optionNode.get("label"), Object.class)) : toStringSafe(value);
                    result.add(new FeedbackOptionDto(value != null ? value : label, label));
                }
            }
            return result;
        } catch (Exception e) {
            return List.of();
        }
    }

    private Map<String, Object> parseResponseMap(JsonNode node) {
        if (node == null || node.isNull() || !node.isObject()) {
            return Map.of();
        }
        try {
            return objectMapper.convertValue(node, new TypeReference<Map<String, Object>>() {});
        } catch (Exception e) {
            return Map.of();
        }
    }

    private JsonNode writeJsonNode(Object value) {
        try {
            return objectMapper.valueToTree(value);
        } catch (Exception e) {
            throw new RuntimeException("Failed to serialize JSON value", e);
        }
    }

    private List<String> parseStringList(JsonNode node) {
        if (node == null || node.isNull() || !node.isArray()) {
            return List.of();
        }
        try {
            return objectMapper.convertValue(node, new TypeReference<List<String>>() {});
        } catch (Exception e) {
            return List.of();
        }
    }

    private List<String> normalizeInterviewTypes(FeedbackScopesDto scopes) {
        if (scopes == null || scopes.interviewTypes() == null) {
            return List.of();
        }
        return scopes.interviewTypes().stream()
                .filter(Objects::nonNull)
                .map(String::trim)
                .filter(value -> !value.isBlank())
                .map(String::toUpperCase)
                .distinct()
                .toList();
    }

    private String normalizeInterviewTypeFilter(String interviewType) {
        if (interviewType == null || interviewType.isBlank()) {
            return null;
        }
        return interviewType.trim().toUpperCase();
    }

    private void validateFormScopes(FeedbackScopesDto scopes) {
        if (scopes == null
                || scopes.departmentIds() == null
                || scopes.departmentIds().isEmpty()) {
            throw new IllegalArgumentException("Department is required");
        }
        if (normalizeInterviewTypes(scopes).isEmpty()) {
            throw new IllegalArgumentException("Interview type is required");
        }
    }

    private String toStringSafe(Object value) {
        return value == null ? null : String.valueOf(value);
    }
}
