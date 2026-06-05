package com.nemal.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.nemal.dto.*;
import com.nemal.entity.*;
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
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class FeedbackService {

    private static final Logger logger = LoggerFactory.getLogger(FeedbackService.class);

    private final FeedbackFormRepository feedbackFormRepository;
    private final FeedbackQuestionRepository feedbackQuestionRepository;
    private final FeedbackResponseRepository feedbackResponseRepository;
    private final InterviewScheduleRepository interviewScheduleRepository;
    private final ObjectMapper objectMapper;

    @Transactional(readOnly = true)
    public FeedbackFormDto getActiveFeedbackForm() {
        FeedbackForm form = feedbackFormRepository.findFirstByIsActiveTrueOrderByIdDesc()
                .orElseThrow(() -> new RuntimeException("No active feedback form found"));

        List<FeedbackQuestionDto> questions = feedbackQuestionRepository
                .findByFormIdAndIsActiveTrueOrderByDisplayOrderAsc(form.getId())
                .stream()
                .map(this::toQuestionDto)
                .toList();

        List<FeedbackQuestionDto> obligatoryQuestions = feedbackQuestionRepository
                .findByCategoryEqualsIgnoreCaseAndIsActiveTrueOrderByDisplayOrderAsc("obligatory")
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
                parseLongList(form.getDesignationIdsJson())
            ),
            questions,
            obligatoryQuestions
        );
    }

    @Transactional
    public FeedbackFormDto createForm(CreateFeedbackFormDto dto) {
        FeedbackForm form = FeedbackForm.builder()
            .name(dto.name())
            .description(dto.description())
            .departmentIdsJson(writeJsonNode(dto.scopes() == null ? List.of() : dto.scopes().departmentIds()))
            .designationIdsJson(writeJsonNode(dto.scopes() == null ? List.of() : dto.scopes().designationIds()))
            .seriesKey(UUID.randomUUID().toString())
            .versionNumber(1)
            .isActive(true)
            .build();

        FeedbackForm saved = feedbackFormRepository.save(form);

        if (dto.questions() != null) {
            int order = 1;
            for (CreateFeedbackQuestionDto qdto : dto.questions()) {
                FeedbackQuestion q = FeedbackQuestion.builder()
                        .form(saved)
                        .displayOrder(qdto.order() == null ? order : qdto.order())
                        .label(qdto.label())
                        .category(qdto.category())
                        .type(qdto.type())
                        .required(qdto.required())
                        .commentsEnabled(qdto.commentsEnabled())
                        .placeholder(qdto.placeholder())
                        .helpText(qdto.helpText())
                        .optionsJson(writeJsonNode(qdto.options() == null ? List.of() : qdto.options()))
                        .isActive(true)
                        .build();
                feedbackQuestionRepository.save(q);
                order++;
            }
        }

        return getActiveFeedbackForm();
    }

    @Transactional
    public FeedbackFormDto updateForm(Long formId, CreateFeedbackFormDto dto) {
        FeedbackForm currentForm = feedbackFormRepository.findById(formId)
                .orElseThrow(() -> new RuntimeException("Feedback form not found: " + formId));
        // If the update contains no questions, treat it as a scope/name/description update
        // and modify the existing form in-place without creating a new version.
        if (dto.questions() == null || dto.questions().isEmpty()) {
            currentForm.setName(dto.name());
            currentForm.setDescription(dto.description());
            currentForm.setDepartmentIdsJson(writeJsonNode(dto.scopes() == null ? List.of() : dto.scopes().departmentIds()));
            currentForm.setDesignationIdsJson(writeJsonNode(dto.scopes() == null ? List.of() : dto.scopes().designationIds()));
            FeedbackForm updated = feedbackFormRepository.save(currentForm);
            return getFormById(updated.getId());
        }

        // If questions are provided, compare them with the current active questions.
        // Only create a new version when questions were added, removed, or edited.
        List<FeedbackQuestionDto> existingQuestions = feedbackQuestionRepository
                .findByFormIdAndIsActiveTrueOrderByDisplayOrderAsc(currentForm.getId())
                .stream()
                .map(this::toQuestionDto)
                .toList();

        // Normalize existing questions to a list of maps (exclude database ids)
        List<Map<String, Object>> existingNormalized = new java.util.ArrayList<>();
        for (FeedbackQuestionDto q : existingQuestions) {
            Map<String, Object> m = new java.util.HashMap<>();
            m.put("order", q.order());
            m.put("label", q.label());
            m.put("category", q.category());
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
            FeedbackForm updated = feedbackFormRepository.save(currentForm);
            return getFormById(updated.getId());
        }

        // Otherwise questions are present and different — create a new version (immutable history for questions)
        FeedbackForm form = FeedbackForm.builder()
            .name(dto.name())
            .description(dto.description())
            .departmentIdsJson(writeJsonNode(dto.scopes() == null ? List.of() : dto.scopes().departmentIds()))
            .designationIdsJson(writeJsonNode(dto.scopes() == null ? List.of() : dto.scopes().designationIds()))
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
                FeedbackQuestion q = FeedbackQuestion.builder()
                .form(savedForm)
                        .displayOrder(qdto.order() == null ? order : qdto.order())
                        .label(qdto.label())
                        .category(qdto.category())
                        .type(qdto.type())
                        .required(qdto.required())
                        .commentsEnabled(qdto.commentsEnabled())
                        .placeholder(qdto.placeholder())
                        .helpText(qdto.helpText())
                        .optionsJson(writeJsonNode(qdto.options() == null ? List.of() : qdto.options()))
                        .isActive(true)
                        .build();
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

        FeedbackQuestion q = FeedbackQuestion.builder()
                .form(form)
                .displayOrder(dto.order() == null ? 1 : dto.order())
                .label(dto.label())
                .category(dto.category())
                .type(dto.type())
                .required(dto.required())
                .commentsEnabled(dto.commentsEnabled())
                .placeholder(dto.placeholder())
                .helpText(dto.helpText())
            .optionsJson(writeJsonNode(dto.options() == null ? List.of() : dto.options()))
                .isActive(true)
                .build();

        FeedbackQuestion saved = feedbackQuestionRepository.save(q);
        return toQuestionDto(saved);
    }

    @Transactional
    public FeedbackQuestionDto updateQuestion(Long questionId, CreateFeedbackQuestionDto dto) {
        FeedbackQuestion q = feedbackQuestionRepository.findById(questionId)
                .orElseThrow(() -> new RuntimeException("Feedback question not found: " + questionId));

        q.setDisplayOrder(dto.order() == null ? q.getDisplayOrder() : dto.order());
        q.setLabel(dto.label());
        q.setCategory(dto.category());
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

        return toResponseDto(saved);
    }

    @Transactional(readOnly = true)
    public FeedbackResponseDto getFeedbackForInterview(Long interviewScheduleId) {
        FeedbackResponse response = feedbackResponseRepository.findByInterviewScheduleId(interviewScheduleId)
                .orElseThrow(() -> new RuntimeException("Feedback not found for interview schedule: " + interviewScheduleId));
        return toResponseDto(response);
    }

    private FeedbackQuestionDto toQuestionDto(FeedbackQuestion question) {
        return new FeedbackQuestionDto(
            question.getId(),
            question.getDisplayOrder(),
                question.getLabel(),
                question.getCategory(),
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
            .findByFormIdAndIsActiveTrueOrderByDisplayOrderAsc(form.getId())
            .stream()
            .map(this::toQuestionDto)
            .toList();
        List<FeedbackQuestionDto> obligatoryQuestions = feedbackQuestionRepository
                .findByCategoryEqualsIgnoreCaseAndIsActiveTrueOrderByDisplayOrderAsc("obligatory")
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
                parseLongList(form.getDesignationIdsJson())
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
                    .findByFormIdAndIsActiveTrueOrderByDisplayOrderAsc(f.getId())
                    .stream()
                    .map(this::toQuestionDto)
                    .toList();

                       List<FeedbackQuestionDto> obligatoryQuestions = feedbackQuestionRepository
                .findByCategoryEqualsIgnoreCaseAndIsActiveTrueOrderByDisplayOrderAsc("obligatory")
                .stream()
                .map(this::toQuestionDto)
                .toList();
                return new FeedbackFormDto(
                    f.getId(),
                    f.getName(),
                    f.getDescription(),
                    f.isActive(),
                    f.getVersionNumber(),
                    new FeedbackScopesDto(parseLongList(f.getDepartmentIdsJson()), parseLongList(f.getDesignationIdsJson())),
                    questions,
                    obligatoryQuestions
                );
            })
            .toList();
        }


    @Transactional(readOnly = true)
    public List<FeedbackFormDto> listFilteredFormsByDepartmentAndDesignation(CandidateFormFilterDto dto) {
        return feedbackFormRepository.findActiveFormsByDepartmentAndDesignation(dto.departmentId(), dto.targetDesignationId())
                .stream()
                .sorted(Comparator.comparing(FeedbackForm::getSeriesKey).thenComparing(FeedbackForm::getVersionNumber, Comparator.nullsLast(Comparator.reverseOrder())))
                .map(f -> {
                    List<FeedbackQuestionDto> questions = feedbackQuestionRepository
                            .findByFormIdAndIsActiveTrueOrderByDisplayOrderAsc(f.getId())
                            .stream()
                            .map(this::toQuestionDto)
                            .toList();

                    List<FeedbackQuestionDto> obligatoryQuestions = feedbackQuestionRepository
                .findByCategoryEqualsIgnoreCaseAndIsActiveTrueOrderByDisplayOrderAsc("obligatory")
                .stream()
                .map(this::toQuestionDto)
                .toList();
                    return new FeedbackFormDto(
                            f.getId(),
                            f.getName(),
                            f.getDescription(),
                            f.isActive(),
                            f.getVersionNumber(),
                            new FeedbackScopesDto(parseLongList(f.getDepartmentIdsJson()), parseLongList(f.getDesignationIdsJson())),
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
                response.getInterviewer().getId(),
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

    private String toStringSafe(Object value) {
        return value == null ? null : String.valueOf(value);
    }
}
