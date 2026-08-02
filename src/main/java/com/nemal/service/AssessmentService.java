package com.nemal.service;

import com.nemal.dto.AssessmentReviewerDto;
import com.nemal.dto.AssessmentScheduleDto;
import com.nemal.dto.AssignAssessmentReviewersDto;
import com.nemal.entity.AssessmentReviewer;
import com.nemal.entity.Candidate;
import com.nemal.entity.InterviewRequest;
import com.nemal.entity.InterviewSchedule;
import com.nemal.entity.User;
import com.nemal.enums.AssessmentPhase;
import com.nemal.enums.InterviewStatus;
import com.nemal.enums.PipelineAuditActionType;
import com.nemal.enums.Role;
import com.nemal.repository.AssessmentReviewerRepository;
import com.nemal.repository.CandidateRepository;
import com.nemal.repository.FeedbackResponseRepository;
import com.nemal.repository.InterviewScheduleRepository;
import com.nemal.repository.UserRepository;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;

@Service
public class AssessmentService {

    private static final long MAX_FILE_BYTES = 10L * 1024 * 1024;

    private final InterviewScheduleRepository interviewScheduleRepository;
    private final AssessmentReviewerRepository assessmentReviewerRepository;
    private final UserRepository userRepository;
    private final FeedbackResponseRepository feedbackResponseRepository;
    private final InterviewTypeService interviewTypeService;
    private final MasterStepService masterStepService;
    private final CandidateStepPipelineService candidateStepPipelineService;
    private final CandidatePipelineAuditService candidatePipelineAuditService;
    private final NotificationService notificationService;
    private final CandidateRepository candidateRepository;

    public AssessmentService(
            InterviewScheduleRepository interviewScheduleRepository,
            AssessmentReviewerRepository assessmentReviewerRepository,
            UserRepository userRepository,
            FeedbackResponseRepository feedbackResponseRepository,
            InterviewTypeService interviewTypeService,
            MasterStepService masterStepService,
            CandidateStepPipelineService candidateStepPipelineService,
            CandidatePipelineAuditService candidatePipelineAuditService,
            NotificationService notificationService,
            CandidateRepository candidateRepository
    ) {
        this.interviewScheduleRepository = interviewScheduleRepository;
        this.assessmentReviewerRepository = assessmentReviewerRepository;
        this.userRepository = userRepository;
        this.feedbackResponseRepository = feedbackResponseRepository;
        this.interviewTypeService = interviewTypeService;
        this.masterStepService = masterStepService;
        this.candidateStepPipelineService = candidateStepPipelineService;
        this.candidatePipelineAuditService = candidatePipelineAuditService;
        this.notificationService = notificationService;
        this.candidateRepository = candidateRepository;
    }

    @Transactional
    public AssessmentScheduleDto getAssessment(Long scheduleId) {
        InterviewSchedule schedule = requireAssessmentSchedule(scheduleId);
        healLegacyReceivedPipelineStatus(schedule);
        return toDto(schedule);
    }

    @Transactional
    public AssessmentScheduleDto uploadAssessmentFile(User actor, Long scheduleId, MultipartFile file) {
        requireHrOrAdmin(actor);
        InterviewSchedule schedule = requireAssessmentSchedule(scheduleId);
        validateFile(file);
        try {
            schedule.setAssessmentFileName(safeFileName(file.getOriginalFilename()));
            schedule.setAssessmentContentType(resolveContentType(file));
            schedule.setAssessmentFileSize(file.getSize());
            schedule.setAssessmentFileData(file.getBytes());
            schedule.setAssessmentUploadedAt(LocalDateTime.now());
            if (schedule.getAssessmentPhase() == null) {
                schedule.setAssessmentPhase(AssessmentPhase.AWAITING);
            }
            return toDto(interviewScheduleRepository.save(schedule));
        } catch (IOException e) {
            throw new RuntimeException("Failed to read uploaded assessment file", e);
        }
    }

    @Transactional(readOnly = true)
    public ResponseEntity<Resource> downloadAssessmentFile(User actor, Long scheduleId) {
        InterviewSchedule schedule = requireAssessmentSchedule(scheduleId);
        assertCanDownload(actor, schedule);
        if (schedule.getAssessmentFileData() == null || schedule.getAssessmentFileName() == null) {
            throw new RuntimeException("No assessment file uploaded yet");
        }
        // Re-load with file bytes (LAZY)
        InterviewSchedule withFile = interviewScheduleRepository.findById(scheduleId)
                .orElseThrow(() -> new RuntimeException("Interview schedule not found: " + scheduleId));
        ByteArrayResource resource = new ByteArrayResource(withFile.getAssessmentFileData());
        String contentType = withFile.getAssessmentContentType() != null
                ? withFile.getAssessmentContentType()
                : MediaType.APPLICATION_OCTET_STREAM_VALUE;
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"" + withFile.getAssessmentFileName() + "\"")
                .contentType(MediaType.parseMediaType(contentType))
                .contentLength(withFile.getAssessmentFileSize() != null
                        ? withFile.getAssessmentFileSize()
                        : withFile.getAssessmentFileData().length)
                .body(resource);
    }

    @Transactional
    public AssessmentScheduleDto markReceived(User actor, Long scheduleId) {
        requireHrOrAdmin(actor);
        InterviewSchedule schedule = requireAssessmentSchedule(scheduleId);
        if (schedule.getAssessmentFileName() == null) {
            throw new RuntimeException("Upload the assessment file before marking it as received");
        }
        if (schedule.getAssessmentPhase() == AssessmentPhase.RECEIVED
                || schedule.getAssessmentPhase() == AssessmentPhase.UNDER_REVIEW
                || schedule.getAssessmentPhase() == AssessmentPhase.COMPLETED) {
            healLegacyReceivedPipelineStatus(schedule);
            return toDto(schedule);
        }

        schedule.setAssessmentPhase(AssessmentPhase.RECEIVED);
        schedule = interviewScheduleRepository.save(schedule);

        // Keep the same interview-type round status (e.g. MOCK_EXAM_ROUND). Do not create
        // a separate "{TYPE}_RECEIVED" pipeline step — the UI labels the existing step
        // using assessmentPhase.
        InterviewRequest request = schedule.getRequest();
        Candidate candidate = request != null ? request.getCandidate() : null;
        if (candidate != null) {
            String roundKey = interviewTypeService.roundStatusKey(schedule.getInterviewType());
            String statusKey = healLegacyReceivedPipelineStatus(schedule);
            if (statusKey == null) {
                statusKey = candidate.getMasterStep() != null
                        ? candidate.getMasterStep().getStatusKey()
                        : roundKey;
            }
            candidatePipelineAuditService.recordStatusChange(
                    candidate.getId(),
                    statusKey,
                    statusKey,
                    PipelineAuditActionType.ASSESSMENT_RECEIVED,
                    actor,
                    interviewTypeService.labelForCode(schedule.getInterviewType()) + " assessment received");
        }
        return toDto(schedule);
    }

    /**
     * Moves candidates off legacy "{CODE}_RECEIVED" master statuses onto the type's round key.
     * @return the candidate's status key after healing (or null if no candidate)
     */
    private String healLegacyReceivedPipelineStatus(InterviewSchedule schedule) {
        InterviewRequest request = schedule.getRequest();
        Candidate candidate = request != null ? request.getCandidate() : null;
        if (candidate == null) {
            return null;
        }
        String roundKey = interviewTypeService.roundStatusKey(schedule.getInterviewType());
        String oldStatusKey = candidate.getMasterStep() != null
                ? candidate.getMasterStep().getStatusKey()
                : null;
        if (oldStatusKey != null
                && oldStatusKey.toUpperCase(Locale.ROOT).endsWith("_RECEIVED")
                && roundKey != null
                && !roundKey.equalsIgnoreCase(oldStatusKey)) {
            masterStepService.assignByStatusKey(candidate, roundKey);
            candidateRepository.save(candidate);
            candidateStepPipelineService.updatePipelineOnStatusChange(
                    candidate.getId(), roundKey, oldStatusKey, false);
            return roundKey;
        }
        return oldStatusKey;
    }

    @Transactional
    public AssessmentScheduleDto assignReviewers(User actor, Long scheduleId, AssignAssessmentReviewersDto dto) {
        requireHrOrAdmin(actor);
        InterviewSchedule schedule = requireAssessmentSchedule(scheduleId);
        if (schedule.getAssessmentPhase() != AssessmentPhase.RECEIVED
                && schedule.getAssessmentPhase() != AssessmentPhase.UNDER_REVIEW) {
            throw new RuntimeException("Mark the assessment as received before assigning reviewers");
        }
        if (dto == null || dto.reviewerUserIds() == null || dto.reviewerUserIds().isEmpty()) {
            throw new RuntimeException("Select at least one reviewer");
        }

        Set<Long> desiredIds = new HashSet<>();
        for (Long id : dto.reviewerUserIds()) {
            if (id != null) desiredIds.add(id);
        }
        if (desiredIds.isEmpty()) {
            throw new RuntimeException("Select at least one reviewer");
        }

        List<AssessmentReviewer> existing = assessmentReviewerRepository
                .findByInterviewScheduleIdOrderByAssignedAtAsc(scheduleId);
        Set<Long> alreadyAssigned = new HashSet<>();
        for (AssessmentReviewer ar : existing) {
            alreadyAssigned.add(ar.getReviewer().getId());
        }

        List<User> newlyAssigned = new ArrayList<>();
        for (Long reviewerId : desiredIds) {
            if (alreadyAssigned.contains(reviewerId)) {
                continue;
            }
            User reviewer = userRepository.findById(reviewerId)
                    .orElseThrow(() -> new RuntimeException("Reviewer not found: " + reviewerId));
            if (!reviewer.isActive()) {
                throw new RuntimeException("Reviewer is inactive: " + reviewer.getFullName());
            }
            assessmentReviewerRepository.save(AssessmentReviewer.builder()
                    .interviewSchedule(schedule)
                    .reviewer(reviewer)
                    .assignedBy(actor)
                    .assignedAt(LocalDateTime.now())
                    .build());
            newlyAssigned.add(reviewer);
        }

        schedule.setAssessmentPhase(AssessmentPhase.UNDER_REVIEW);
        schedule = interviewScheduleRepository.save(schedule);

        for (User reviewer : newlyAssigned) {
            notificationService.sendAssessmentReviewAssignedNotification(schedule, reviewer);
        }
        return toDto(schedule);
    }

    @Transactional(readOnly = true)
    public List<AssessmentReviewerDto> listReviewers(Long scheduleId) {
        requireAssessmentSchedule(scheduleId);
        return mapReviewers(scheduleId);
    }

    @Transactional(readOnly = true)
    public List<AssessmentScheduleDto> listAssignedAssessments(User reviewer) {
        return assessmentReviewerRepository.findByReviewerIdWithSchedule(reviewer.getId()).stream()
                .map(AssessmentReviewer::getInterviewSchedule)
                .filter(Objects::nonNull)
                .filter(s -> s.getAssessmentPhase() != null)
                .map(this::toDto)
                .toList();
    }

    /**
     * After a reviewer submits feedback: if every assigned reviewer has feedback,
     * mark the assessment phase and schedule as COMPLETED and complete the pipeline round.
     *
     * @return true when the assessment was (or already is) fully completed
     */
    @Transactional
    public boolean completeAssessmentIfAllReviewsDone(InterviewSchedule schedule, User actor) {
        if (schedule == null || schedule.getId() == null) {
            return false;
        }
        InterviewSchedule managed = interviewScheduleRepository.findById(schedule.getId()).orElse(schedule);
        if (managed.getAssessmentPhase() == null) {
            return false;
        }
        if (managed.getAssessmentPhase() == AssessmentPhase.COMPLETED
                && managed.getStatus() == InterviewStatus.COMPLETED) {
            return true;
        }

        List<AssessmentReviewer> reviewers = assessmentReviewerRepository
                .findByInterviewScheduleIdOrderByAssignedAtAsc(managed.getId());
        if (reviewers.isEmpty()) {
            return false;
        }

        boolean allSubmitted = reviewers.stream().allMatch(ar ->
                ar.getReviewer() != null
                        && feedbackResponseRepository
                        .findByInterviewScheduleIdAndInterviewerId(managed.getId(), ar.getReviewer().getId())
                        .isPresent());
        if (!allSubmitted) {
            return false;
        }

        return markAssessmentCompleted(managed, actor, "all reviews submitted");
    }

    /**
     * Reviewer (or HR/Admin) explicitly marks the assessment complete from the feedback UI.
     * Requires the acting reviewer to have submitted their own feedback first.
     */
    @Transactional
    public AssessmentScheduleDto markCompletedByReviewer(User actor, Long scheduleId) {
        InterviewSchedule schedule = requireAssessmentSchedule(scheduleId);
        if (schedule.getAssessmentPhase() == AssessmentPhase.COMPLETED
                && schedule.getStatus() == InterviewStatus.COMPLETED) {
            return toDto(schedule);
        }
        if (schedule.getStatus() == InterviewStatus.CANCELLED) {
            throw new RuntimeException("Cannot complete a cancelled assessment");
        }

        boolean isHrOrAdmin = hasRole(actor, Role.HR) || hasRole(actor, Role.ADMIN);
        boolean isAssignedReviewer = assessmentReviewerRepository
                .existsByInterviewScheduleIdAndReviewerId(scheduleId, actor.getId());
        if (!isHrOrAdmin && !isAssignedReviewer) {
            throw new RuntimeException("You are not authorized to complete this assessment");
        }

        boolean hasOwnFeedback = feedbackResponseRepository
                .findByInterviewScheduleIdAndInterviewerId(scheduleId, actor.getId())
                .isPresent();
        if (!hasOwnFeedback && !isHrOrAdmin) {
            throw new RuntimeException("Submit your feedback before marking the assessment complete");
        }
        if (!hasOwnFeedback && isHrOrAdmin) {
            List<AssessmentReviewer> reviewers = assessmentReviewerRepository
                    .findByInterviewScheduleIdOrderByAssignedAtAsc(scheduleId);
            boolean anyFeedback = reviewers.stream().anyMatch(ar ->
                    ar.getReviewer() != null
                            && feedbackResponseRepository
                            .findByInterviewScheduleIdAndInterviewerId(scheduleId, ar.getReviewer().getId())
                            .isPresent());
            if (!anyFeedback && reviewers.isEmpty()) {
                throw new RuntimeException("Feedback must be submitted before completing the assessment");
            }
            if (!anyFeedback && !reviewers.isEmpty()) {
                throw new RuntimeException("At least one reviewer must submit feedback before completing");
            }
        }

        markAssessmentCompleted(schedule, actor, "marked complete by reviewer");
        return toDto(interviewScheduleRepository.findById(scheduleId).orElse(schedule));
    }

    private boolean markAssessmentCompleted(InterviewSchedule schedule, User actor, String reasonSuffix) {
        schedule.setAssessmentPhase(AssessmentPhase.COMPLETED);
        schedule.setStatus(InterviewStatus.COMPLETED);
        schedule.setCompletedAt(LocalDateTime.now());
        interviewScheduleRepository.save(schedule);

        InterviewRequest request = schedule.getRequest();
        Candidate candidate = request != null ? request.getCandidate() : null;
        if (candidate != null) {
            String interviewType = schedule.getInterviewType() != null
                    ? schedule.getInterviewType()
                    : "ASSESSMENT";
            candidateStepPipelineService.completeInterviewRoundStep(candidate.getId(), interviewType);

            String statusKey = candidate.getMasterStep() != null
                    ? candidate.getMasterStep().getStatusKey()
                    : interviewTypeService.roundStatusKey(interviewType);
            String label = interviewTypeService.labelForCode(interviewType);
            candidatePipelineAuditService.recordStatusChange(
                    candidate.getId(),
                    statusKey,
                    statusKey,
                    PipelineAuditActionType.ASSESSMENT_COMPLETED,
                    actor,
                    label + " assessment completed — " + reasonSuffix);
        }
        return true;
    }

    private AssessmentScheduleDto toDto(InterviewSchedule schedule) {
        InterviewRequest request = schedule.getRequest();
        Candidate candidate = request != null ? request.getCandidate() : null;
        String type = schedule.getInterviewType();
        return new AssessmentScheduleDto(
                schedule.getId(),
                request != null ? request.getId() : null,
                candidate != null ? candidate.getId() : null,
                candidate != null ? candidate.getName()
                        : (request != null ? request.getCandidateName() : null),
                type,
                interviewTypeService.labelForCode(type),
                schedule.getAssessmentPhase(),
                schedule.getAssessmentFileName() != null,
                schedule.getAssessmentFileName(),
                schedule.getAssessmentFileSize(),
                schedule.getAssessmentUploadedAt(),
                schedule.getStartDateTime(),
                schedule.getEndDateTime(),
                request != null ? request.getNotes() : null,
                mapReviewers(schedule.getId())
        );
    }

    private List<AssessmentReviewerDto> mapReviewers(Long scheduleId) {
        return assessmentReviewerRepository.findByInterviewScheduleIdOrderByAssignedAtAsc(scheduleId).stream()
                .map(ar -> {
                    User reviewer = ar.getReviewer();
                    boolean submitted = feedbackResponseRepository
                            .findByInterviewScheduleIdAndInterviewerId(scheduleId, reviewer.getId())
                            .isPresent();
                    var designation = reviewer.getCurrentDesignation();
                    var department = reviewer.getDepartment();
                    return new AssessmentReviewerDto(
                            ar.getId(),
                            reviewer.getId(),
                            reviewer.getFullName(),
                            reviewer.getEmail(),
                            designation != null ? designation.getName() : null,
                            department != null ? department.getName() : null,
                            ar.getAssignedAt(),
                            submitted
                    );
                })
                .toList();
    }

    private InterviewSchedule requireAssessmentSchedule(Long scheduleId) {
        InterviewSchedule schedule = interviewScheduleRepository.findByIdWithPostponeDetails(scheduleId)
                .or(() -> interviewScheduleRepository.findById(scheduleId))
                .orElseThrow(() -> new RuntimeException("Interview schedule not found: " + scheduleId));

        if (schedule.getAssessmentPhase() != null) {
            return schedule;
        }
        // Backfill for assessments created before phase column existed
        if (!interviewTypeService.shouldRequireInterviewer(schedule.getInterviewType())) {
            schedule.setAssessmentPhase(AssessmentPhase.AWAITING);
            return interviewScheduleRepository.save(schedule);
        }
        throw new RuntimeException("This schedule is not an assessment");
    }

    private void assertCanDownload(User actor, InterviewSchedule schedule) {
        if (hasRole(actor, Role.HR) || hasRole(actor, Role.ADMIN)) {
            return;
        }
        if (assessmentReviewerRepository.existsByInterviewScheduleIdAndReviewerId(schedule.getId(), actor.getId())) {
            return;
        }
        throw new RuntimeException("You are not allowed to download this assessment");
    }

    private void requireHrOrAdmin(User actor) {
        if (!hasRole(actor, Role.HR) && !hasRole(actor, Role.ADMIN)) {
            throw new RuntimeException("Only HR or Admin can manage assessments");
        }
    }

    private boolean hasRole(User user, Role role) {
        return user.getRoles() != null && user.getRoles().contains(role);
    }

    private void validateFile(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new RuntimeException("Assessment file is required");
        }
        if (file.getSize() > MAX_FILE_BYTES) {
            throw new RuntimeException("Assessment file must be 10 MB or smaller");
        }
        String contentType = resolveContentType(file).toLowerCase(Locale.ROOT);
        if (!contentType.startsWith("image/")
                && !contentType.startsWith("application/")
                && !contentType.startsWith("text/")) {
            throw new RuntimeException("Unsupported assessment file type: " + contentType);
        }
    }

    private String resolveContentType(MultipartFile file) {
        String contentType = file.getContentType();
        return contentType != null && !contentType.isBlank()
                ? contentType
                : MediaType.APPLICATION_OCTET_STREAM_VALUE;
    }

    private String safeFileName(String original) {
        if (original == null || original.isBlank()) {
            return "assessment-submission";
        }
        String cleaned = original.replace("\\", "/");
        int slash = cleaned.lastIndexOf('/');
        return slash >= 0 ? cleaned.substring(slash + 1) : cleaned;
    }
}
