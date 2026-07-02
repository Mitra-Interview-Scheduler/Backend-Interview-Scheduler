package com.nemal.service;

import com.nemal.dto.CreateInterviewRequestDto;
import com.nemal.dto.InterviewRequestDto;
import com.nemal.entity.*;
import com.nemal.enums.InterviewStatus;
import com.nemal.enums.InterviewType;
import com.nemal.enums.MasterStatus;
import com.nemal.enums.RequestStatus;
import com.nemal.enums.PipelineAuditActionType;
import com.nemal.enums.Role;
import com.nemal.enums.SlotStatus;
import com.nemal.repository.*;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class InterviewRequestService {

    private static final Logger logger = LoggerFactory.getLogger(InterviewRequestService.class);

    private final InterviewRequestRepository interviewRequestRepository;
    private final AvailabilitySlotRepository availabilitySlotRepository;
    private final InterviewScheduleRepository interviewScheduleRepository;
    private final CandidateRepository candidateRepository;
    private final DesignationRepository designationRepository;
    private final TechnologyRepository technologyRepository;
    private final TierRepository tierRepository;
    private final NotificationService notificationService;
    private final CandidateStepPipelineService candidateStepPipelineService;
    private final FeedbackResponseRepository feedbackResponseRepository;
    private final InterviewPanelRepository interviewPanelRepository;
    private final MasterStepService masterStepService;
    private final UserRepository userRepository;
    private final CandidatePipelineAuditService candidatePipelineAuditService;

    @Transactional
    public InterviewRequestDto createInterviewRequest(User requestedBy, CreateInterviewRequestDto dto) {

        AvailabilitySlot slot = availabilitySlotRepository.findById(dto.availabilitySlotId())
                .orElseThrow(() -> new RuntimeException("Availability slot not found: " + dto.availabilitySlotId()));

        if (slot.getStatus() != SlotStatus.AVAILABLE) {
            throw new RuntimeException("Slot is not available");
        }

        LocalDateTime bookingStart = dto.preferredStartDateTime() != null
                ? dto.preferredStartDateTime() : slot.getStartDateTime();
        LocalDateTime bookingEnd = dto.preferredEndDateTime() != null
                ? dto.preferredEndDateTime() : slot.getEndDateTime();

        if (bookingStart.isBefore(slot.getStartDateTime().minusSeconds(1))
                || bookingEnd.isAfter(slot.getEndDateTime().plusSeconds(1))) {
            throw new RuntimeException("Booking time must be within the slot's available time");
        }

        Candidate candidate = null;
        if (dto.candidateId() != null) {
            candidate = candidateRepository.findById(dto.candidateId())
                    .orElseThrow(() -> new RuntimeException("Candidate not found: " + dto.candidateId()));
        }
        String candidateName = dto.candidateName() != null ? dto.candidateName()
                : (candidate != null ? candidate.getName() : "Unknown");

        Designation candidateDesignation = null;
        if (dto.candidateDesignationId() != null) {
            candidateDesignation = designationRepository.findById(dto.candidateDesignationId())
                    .orElseThrow(() -> new RuntimeException("Designation not found"));
        }

        List<Technology> technologies = dto.requiredTechnologyIds() != null
                ? technologyRepository.findAllById(dto.requiredTechnologyIds())
                : List.of();

        AvailabilitySlot bookedSlot = splitAndBookSlot(slot, bookingStart, bookingEnd, candidateName);

        User interviewCoordinator = resolveInterviewCoordinator(
                dto.interviewCoordinatorId(),
                dto.interviewCoordinatorDepartmentId());

        InterviewRequest request = InterviewRequest.builder()
                .candidateName(candidateName)
                .candidate(candidate)
                .candidateDesignation(candidateDesignation)
                .preferredStartDateTime(bookingStart)
                .preferredEndDateTime(bookingEnd)
                .requestedBy(requestedBy)
                .assignedInterviewer(slot.getInterviewer())
                .interviewCoordinator(interviewCoordinator)
                .availabilitySlot(bookedSlot)
                .status(RequestStatus.ACCEPTED)
                .respondedAt(LocalDateTime.now())
                .responseNotes("Auto-accepted by HR scheduling")
                .isUrgent(dto.isUrgent())
                .notes(dto.notes())
                .build();

        request.getRequiredTechnologies().addAll(technologies);
        InterviewRequest saved = interviewRequestRepository.save(request);

        InterviewType interviewType = InterviewType.fromValue(dto.interviewType());

        InterviewSchedule schedule = InterviewSchedule.builder()
                .request(saved)
                .interviewer(slot.getInterviewer())
                .startDateTime(bookingStart)
                .endDateTime(bookingEnd)
                .status(InterviewStatus.SCHEDULED)
                .interviewType(interviewType)
                .build();
        schedule = interviewScheduleRepository.save(schedule);

        bookedSlot.setInterviewSchedule(schedule);
        availabilitySlotRepository.save(bookedSlot);
        saved.setInterviewSchedule(schedule);

        if (candidate != null) {
            applyCandidateStatusForScheduledInterview(candidate, interviewType, requestedBy);
        }

        try {
            notificationService.sendInterviewScheduledNotification(saved);
            if (interviewCoordinator != null) {
                notificationService.sendInterviewCoordinatorScheduledNotification(saved);
            }
        } catch (Exception e) {
            logger.warn("Failed to send scheduled notification: {}", e.getMessage());
        }

        return InterviewRequestDto.from(saved);
    }

    private AvailabilitySlot splitAndBookSlot(AvailabilitySlot slot,
                                              LocalDateTime bookStart,
                                              LocalDateTime bookEnd,
                                              String candidateName) {
        boolean isPartialBooking = !bookStart.equals(slot.getStartDateTime())
                || !bookEnd.equals(slot.getEndDateTime());

        if (!isPartialBooking) {
            slot.setStatus(SlotStatus.BOOKED);
            slot.setDescription("Interview: " + candidateName);
            return availabilitySlotRepository.save(slot);
        }

        slot.setActive(false);
        availabilitySlotRepository.save(slot);

        if (bookStart.isAfter(slot.getStartDateTime())) {
            AvailabilitySlot before = AvailabilitySlot.builder()
                    .interviewer(slot.getInterviewer())
                    .startDateTime(slot.getStartDateTime())
                    .endDateTime(bookStart)
                    .status(SlotStatus.AVAILABLE)
                    .description(slot.getDescription())
                    .isActive(true)
                    .build();
            availabilitySlotRepository.save(before);
        }

        AvailabilitySlot booked = AvailabilitySlot.builder()
                .interviewer(slot.getInterviewer())
                .startDateTime(bookStart)
                .endDateTime(bookEnd)
                .status(SlotStatus.BOOKED)
                .description("Interview: " + candidateName)
                .isActive(true)
                .build();
        booked = availabilitySlotRepository.save(booked);

        if (bookEnd.isBefore(slot.getEndDateTime())) {
            AvailabilitySlot after = AvailabilitySlot.builder()
                    .interviewer(slot.getInterviewer())
                    .startDateTime(bookEnd)
                    .endDateTime(slot.getEndDateTime())
                    .status(SlotStatus.AVAILABLE)
                    .description(slot.getDescription())
                    .isActive(true)
                    .build();
            availabilitySlotRepository.save(after);
        }

        return booked;
    }

    // ── Read ──────────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<InterviewRequest> getBookedInterviewSchedule(Long interviewScheduleId) {
        return interviewRequestRepository.findByInterviewScheduleIdWithDetails(interviewScheduleId);
    }

    @Transactional(readOnly = true)
    public List<InterviewRequestDto> getRequestsByUser(Long userId) {
        return interviewRequestRepository.findByRequestedByIdOrderByCreatedAtDesc(userId)
                .stream().map(InterviewRequestDto::from).collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<InterviewRequestDto> getRequestsByUser(Long userId, int limit) {
        int safeLimit = Math.max(1, limit);
        return interviewRequestRepository.findByRequestedByIdOrderByCreatedAtDesc(userId)
                .stream()
                .limit(safeLimit)
                .map(InterviewRequestDto::from)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<InterviewRequestDto> getRequestsByUser(Long userId, Long departmentId, Integer minTierId, Integer exactTierId) {
        Integer minTierOrder = null;
        Integer exactTierOrder = null;
        try {
            if (minTierId != null) {
                var t = tierRepository.findById(Long.valueOf(minTierId)).orElse(null);
                if (t != null) minTierOrder = t.getTierOrder();
            }
            if (exactTierId != null) {
                var t = tierRepository.findById(Long.valueOf(exactTierId)).orElse(null);
                if (t != null) exactTierOrder = t.getTierOrder();
            }
        } catch (Exception e) {
            // ignore mapping errors — fallback to no tier filtering
        }
        return interviewRequestRepository.findByRequestedByIdWithFilters(userId, departmentId, minTierOrder, exactTierOrder)
                .stream().map(InterviewRequestDto::from).collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<InterviewRequestDto> getRequestsByUser(Long userId, int limit, Long departmentId, Integer minTierId, Integer exactTierId) {
        int safeLimit = Math.max(1, limit);
        Integer minTierOrder = null;
        Integer exactTierOrder = null;
        try {
            if (minTierId != null) {
                var t = tierRepository.findById(Long.valueOf(minTierId)).orElse(null);
                if (t != null) minTierOrder = t.getTierOrder();
            }
            if (exactTierId != null) {
                var t = tierRepository.findById(Long.valueOf(exactTierId)).orElse(null);
                if (t != null) exactTierOrder = t.getTierOrder();
            }
        } catch (Exception e) {
            // ignore mapping errors
        }
        return interviewRequestRepository.findByRequestedByIdWithFilters(userId, departmentId, minTierOrder, exactTierOrder)
                .stream()
                .limit(safeLimit)
                .map(InterviewRequestDto::from)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<InterviewRequestDto> getRequestsByCandidate(Long candidateId) {
        return interviewRequestRepository.findByCandidateIdWithSchedule(candidateId)
                .stream().map(InterviewRequestDto::from).collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<InterviewRequestDto> getUpcomingInterviewsForInterviewer(Long interviewerId) {
        return interviewRequestRepository
                .findUpcomingInterviewsForInterviewer(interviewerId, LocalDateTime.now())
                .stream().map(InterviewRequestDto::from).collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<InterviewRequestDto> getUpcomingInterviewsForInterviewer(Long interviewerId, int limit) {
        int safeLimit = Math.max(1, limit);
        return interviewRequestRepository
                .findUpcomingInterviewsForInterviewer(
                        interviewerId,
                        LocalDateTime.now(),
                        PageRequest.of(0, safeLimit)
                )
                .stream().map(InterviewRequestDto::from).collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<InterviewRequestDto> getInterviewsForInterviewer(Long interviewerId) {
        return interviewRequestRepository.findByAssignedInterviewerId(interviewerId)
                .stream().map(InterviewRequestDto::from).collect(Collectors.toList());
    }

    // ── Mutations ─────────────────────────────────────────────────────────────

    @Transactional
    public InterviewRequestDto respondToRequest(User interviewer, Long requestId,
                                                String action, String notes) {
        InterviewRequest request = interviewRequestRepository.findById(requestId)
                .orElseThrow(() -> new RuntimeException("Request not found: " + requestId));

        if (!request.getAssignedInterviewer().getId().equals(interviewer.getId())) {
            throw new RuntimeException("You are not assigned to this request");
        }

        if ("ACCEPT".equalsIgnoreCase(action)) {
            request.setStatus(RequestStatus.ACCEPTED);
        } else if ("DECLINE".equalsIgnoreCase(action)) {
            request.setStatus(RequestStatus.REJECTED);
            if (request.getAvailabilitySlot() != null) {
                AvailabilitySlot slot = request.getAvailabilitySlot();
                slot.setStatus(SlotStatus.AVAILABLE);
                availabilitySlotRepository.save(slot);
            }
        } else {
            throw new RuntimeException("Invalid action: " + action);
        }

        request.setRespondedAt(LocalDateTime.now());
        request.setResponseNotes(notes);
        return InterviewRequestDto.from(interviewRequestRepository.save(request));
    }

    @Transactional
    public void cancelRequest(User user, Long requestId) {
        boolean isHrOrAdmin = user.getRoles().contains(Role.HR) || user.getRoles().contains(Role.ADMIN);
        if (!isHrOrAdmin) {
            throw new RuntimeException("Only HR or Admin users can cancel interview requests");
        }

        logger.info("HR user {} cancelling request {}", user.getId(), requestId);

        InterviewRequest request = interviewRequestRepository.findById(requestId)
                .orElseThrow(() -> new RuntimeException("Request not found: " + requestId));

        if (request.getStatus() == RequestStatus.CANCELLED) {
            throw new RuntimeException("Request is already cancelled");
        }

        // ── Step 1: Fix the AvailabilitySlot ─────────────────────────────────
        AvailabilitySlot slot = request.getAvailabilitySlot();
        if (slot != null) {
            logger.info("Slot {} current state: status={}, active={}", slot.getId(), slot.getStatus(), slot.isActive());

            slot.setInterviewSchedule(null);
            slot.setStatus(SlotStatus.AVAILABLE);
            slot.setActive(true);
            slot.setDescription(null);
            availabilitySlotRepository.save(slot);
            logger.info("Slot {} restored: status=AVAILABLE, active=true", slot.getId());

            mergeAdjacentSlots(slot);
        } else {
            logger.warn("Request {} had no linked slot — nothing to restore", requestId);
        }

        // ── Step 2: Cancel the InterviewSchedule ─────────────────────────────
        interviewScheduleRepository.findByRequestId(requestId).ifPresent(schedule -> {
            logger.info("Cancelling InterviewSchedule {}", schedule.getId());
            schedule.setStatus(InterviewStatus.CANCELLED);
            interviewScheduleRepository.save(schedule);
        });

        // ── Step 3: Mark request CANCELLED ───────────────────────────────────
        request.setStatus(RequestStatus.CANCELLED);
        request.setAvailabilitySlot(null);
        interviewRequestRepository.save(request);
        logger.info("Request {} marked CANCELLED", requestId);

        // ── Step 4: Notify interviewer ────────────────────────────────────────
        try {
            InterviewRequest forNotification = interviewRequestRepository.findById(requestId).orElse(request);
            notificationService.sendInterviewCancelledNotification(forNotification);
            logger.info("Cancellation notification sent");
        } catch (Exception e) {
            logger.warn("Failed to send cancellation notification: {}", e.getMessage());
        }

        // ── Step 5: Reset candidate status if no other active interviews ──────
        if (request.getCandidate() != null) {
            Candidate candidate = request.getCandidate();
            long activeCount = interviewRequestRepository
                    .findByCandidateId(candidate.getId())
                    .stream()
                    .filter(r -> r.getStatus() == RequestStatus.ACCEPTED
                            && !r.getId().equals(requestId))
                    .count();
            if (activeCount == 0) {
                InterviewType interviewType = interviewScheduleRepository.findByRequestId(requestId)
                        .map(InterviewSchedule::getInterviewType)
                        .orElse(InterviewType.TECHNICAL);
                MasterStatus resetStatus = interviewType.statusAfterInterviewCancel();
                MasterStatus previousStatus = candidate.getStatus();
                masterStepService.assignStatus(candidate, resetStatus);
                candidateRepository.save(candidate);
                candidateStepPipelineService.restorePipelineAfterInterviewCancel(
                        candidate.getId(), interviewType);
                candidatePipelineAuditService.recordStatusChange(
                        candidate.getId(),
                        resetStatus,
                        previousStatus,
                        PipelineAuditActionType.INTERVIEW_CANCELLED,
                        user,
                        "Interview cancelled");
                logger.info("Candidate {} reset to {} after {} interview cancel",
                        candidate.getId(), resetStatus, interviewType);
            }
        }
    }

    @Transactional
    public InterviewRequestDto completeInterview(User user, Long scheduleId) {
        InterviewSchedule schedule = interviewScheduleRepository.findById(scheduleId)
                .orElseThrow(() -> new RuntimeException("Interview schedule not found: " + scheduleId));

        InterviewRequest request = resolveInterviewRequest(schedule, scheduleId);
        InterviewPanel panel = request.getPanel();
        Set<InterviewRequest> panelRequests = panel != null
                ? loadPanelRequests(panel.getId())
                : Set.of(request);

        if (panel != null) {
            boolean anyCompleted = panelRequests.stream()
                    .map(InterviewRequest::getInterviewSchedule)
                    .filter(Objects::nonNull)
                    .anyMatch(s -> s.getStatus() == InterviewStatus.COMPLETED);
            if (anyCompleted) {
                throw new RuntimeException("Interview is already completed");
            }
        } else {
            if (schedule.getStatus() == InterviewStatus.COMPLETED) {
                throw new RuntimeException("Interview is already completed");
            }
        }

        if (schedule.getStatus() == InterviewStatus.CANCELLED) {
            throw new RuntimeException("Cannot complete a cancelled interview");
        }

        if (!hasPanelFeedback(panelRequests, scheduleId)) {
            throw new RuntimeException("Feedback must be submitted before completing the interview");
        }

        boolean isHrOrAdmin = user.getRoles().contains(Role.HR) || user.getRoles().contains(Role.ADMIN);
        boolean isAssignedInterviewer = panel != null
                ? isPanelInterviewer(user, panelRequests)
                : schedule.getInterviewer() != null && schedule.getInterviewer().getId().equals(user.getId());
        if (!isAssignedInterviewer && !isHrOrAdmin) {
            throw new RuntimeException("You are not authorized to complete this interview");
        }

        LocalDateTime completedAt = LocalDateTime.now();
        if (panel != null) {
            List<InterviewSchedule> panelSchedules = interviewScheduleRepository.findByPanelId(panel.getId());
            if (panelSchedules.isEmpty()) {
                for (InterviewRequest panelRequest : panelRequests) {
                    InterviewSchedule panelSchedule = resolveScheduleForRequest(panelRequest);
                    if (panelSchedule != null && panelSchedule.getStatus() != InterviewStatus.CANCELLED) {
                        panelSchedules.add(panelSchedule);
                    }
                }
            }
            for (InterviewSchedule panelSchedule : panelSchedules) {
                if (panelSchedule.getStatus() == InterviewStatus.CANCELLED) {
                    continue;
                }
                panelSchedule.setStatus(InterviewStatus.COMPLETED);
                panelSchedule.setCompletedAt(completedAt);
                interviewScheduleRepository.save(panelSchedule);
            }
            logger.info("Panel interview {} marked COMPLETED for {} schedule(s) by user {}",
                    panel.getId(), panelSchedules.size(), user.getId());
        } else {
            schedule.setStatus(InterviewStatus.COMPLETED);
            schedule.setCompletedAt(completedAt);
            interviewScheduleRepository.save(schedule);
            logger.info("Interview schedule {} marked COMPLETED by user {}", scheduleId, user.getId());
        }

        return InterviewRequestDto.from(request);
    }

    private InterviewRequest resolveInterviewRequest(InterviewSchedule schedule, Long scheduleId) {
        InterviewRequest request = schedule.getRequest();
        if (request == null) {
            request = interviewRequestRepository.findByInterviewScheduleId(scheduleId)
                    .stream()
                    .findFirst()
                    .orElseThrow(() -> new RuntimeException("Interview request not found for schedule: " + scheduleId));
        }
        return request;
    }

    private Set<InterviewRequest> loadPanelRequests(Long panelId) {
        return interviewPanelRepository.findByIdWithDetails(panelId)
                .map(InterviewPanel::getPanelRequests)
                .orElse(Set.of());
    }

    private boolean hasPanelFeedback(Set<InterviewRequest> panelRequests, Long scheduleId) {
        if (panelRequests.size() <= 1) {
            return feedbackResponseRepository.existsByInterviewScheduleId(scheduleId);
        }
        return panelRequests.stream()
                .map(InterviewRequest::getInterviewSchedule)
                .filter(Objects::nonNull)
                .anyMatch(s -> feedbackResponseRepository.existsByInterviewScheduleId(s.getId()));
    }

    private boolean isPanelInterviewer(User user, Set<InterviewRequest> panelRequests) {
        return panelRequests.stream()
                .map(InterviewRequest::getAssignedInterviewer)
                .filter(Objects::nonNull)
                .anyMatch(interviewer -> interviewer.getId().equals(user.getId()));
    }

    @Transactional(readOnly = true)
    public InterviewStatus resolveEffectiveInterviewStatus(InterviewSchedule schedule) {
        if (schedule == null || schedule.getStatus() == null) {
            return null;
        }
        if (schedule.getStatus() == InterviewStatus.COMPLETED
                || schedule.getStatus() == InterviewStatus.CANCELLED) {
            return schedule.getStatus();
        }

        InterviewRequest request = schedule.getRequest();
        if (request == null || request.getPanel() == null) {
            return schedule.getStatus();
        }

        boolean anyCompleted = interviewScheduleRepository.findByPanelId(request.getPanel().getId())
                .stream()
                .anyMatch(panelSchedule -> panelSchedule.getStatus() == InterviewStatus.COMPLETED);
        return anyCompleted ? InterviewStatus.COMPLETED : schedule.getStatus();
    }

    private InterviewSchedule resolveScheduleForRequest(InterviewRequest request) {
        if (request.getInterviewSchedule() != null) {
            return request.getInterviewSchedule();
        }
        return interviewScheduleRepository.findByRequestId(request.getId()).orElse(null);
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    private void applyCandidateStatusForScheduledInterview(Candidate candidate,
                                                           InterviewType interviewType,
                                                           User changedBy) {
        MasterStatus targetStatus = interviewType.toCandidateStatus();
        MasterStatus oldStatus = candidate.getStatus();
        masterStepService.assignStatus(candidate, targetStatus);
        candidateRepository.save(candidate);
        candidateStepPipelineService.updatePipelineOnStatusChange(
                candidate.getId(), targetStatus, oldStatus, true);
        candidatePipelineAuditService.recordStatusChange(
                candidate.getId(),
                targetStatus,
                oldStatus,
                PipelineAuditActionType.INTERVIEW_SCHEDULED,
                changedBy,
                interviewType.name() + " interview scheduled");
    }

    private void mergeAdjacentSlots(AvailabilitySlot restoredSlot) {
        Long interviewerId = restoredSlot.getInterviewer().getId();
        LocalDateTime mergedStart = restoredSlot.getStartDateTime();
        LocalDateTime mergedEnd   = restoredSlot.getEndDateTime();
        boolean changed = false;

        var before = availabilitySlotRepository
                .findActiveAvailableSlotEndingAt(interviewerId, mergedStart);
        if (before.isPresent()) {
            logger.info("Merging before-fragment slot {} into restored slot {}",
                    before.get().getId(), restoredSlot.getId());
            mergedStart = before.get().getStartDateTime();
            before.get().setActive(false);
            availabilitySlotRepository.save(before.get());
            changed = true;
        }

        var after = availabilitySlotRepository
                .findActiveAvailableSlotStartingAt(interviewerId, mergedEnd);
        if (after.isPresent()) {
            logger.info("Merging after-fragment slot {} into restored slot {}",
                    after.get().getId(), restoredSlot.getId());
            mergedEnd = after.get().getEndDateTime();
            after.get().setActive(false);
            availabilitySlotRepository.save(after.get());
            changed = true;
        }

        if (changed) {
            restoredSlot.setStartDateTime(mergedStart);
            restoredSlot.setEndDateTime(mergedEnd);
            availabilitySlotRepository.save(restoredSlot);
            logger.info("Slot {} merged to window {} – {}", restoredSlot.getId(), mergedStart, mergedEnd);
        }
    }

    private User resolveInterviewCoordinator(Long coordinatorId, Long expectedDepartmentId) {
        if (coordinatorId == null) {
            return null;
        }

        User user = userRepository.findById(coordinatorId)
                .orElseThrow(() -> new IllegalArgumentException("Interview coordinator not found"));

        if (!user.isActive()) {
            throw new IllegalArgumentException("Interview coordinator is inactive");
        }

        if (expectedDepartmentId != null) {
            if (user.getDepartment() == null
                    || !user.getDepartment().getId().equals(expectedDepartmentId)) {
                throw new IllegalArgumentException(
                        "Interview coordinator must belong to the selected department");
            }
        }

        return user;
    }
}