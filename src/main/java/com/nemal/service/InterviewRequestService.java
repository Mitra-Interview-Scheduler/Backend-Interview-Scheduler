package com.nemal.service;

import com.nemal.dto.CreateInterviewRequestDto;
import com.nemal.dto.GoogleCalendarExternalEventDto;
import com.nemal.dto.InterviewRequestDto;
import com.nemal.dto.InterviewerConflictsDto;
import com.nemal.entity.*;
import com.nemal.enums.InterviewStatus;
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
import java.util.HashSet;
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
    private final CalendarSyncService calendarSyncService;
    private final PanelInterviewService panelInterviewService;
    private final InterviewTypeService interviewTypeService;

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

        if (!Boolean.TRUE.equals(dto.acknowledgeCalendarConflict())) {
            assertNoSchedulingConflicts(
                    List.of(slot.getInterviewer().getId()),
                    bookingStart,
                    bookingEnd);
        }

        Candidate candidate = null;
        if (dto.candidateId() != null) {
            candidate = candidateRepository.findById(dto.candidateId())
                    .orElseThrow(() -> new RuntimeException("Candidate not found: " + dto.candidateId()));
        }
        String candidateName = dto.candidateName() != null ? dto.candidateName()
                : (candidate != null ? candidate.getName() : "Unknown");
        String candidateInviteEmail = resolveCandidateInviteEmail(dto.candidateEmail(), candidate);

        Designation candidateDesignation = null;
        if (dto.candidateDesignationId() != null) {
            candidateDesignation = designationRepository.findById(dto.candidateDesignationId())
                    .orElseThrow(() -> new RuntimeException("Designation not found"));
        }

        List<Technology> technologies = dto.requiredTechnologyIds() != null
                ? technologyRepository.findAllById(dto.requiredTechnologyIds())
                : List.of();

        AvailabilitySlot originalSlot = slot;
        AvailabilitySlot bookedSlot = splitAndBookSlot(slot, bookingStart, bookingEnd, candidateName);

        User interviewCoordinator = resolveInterviewCoordinator(
                dto.interviewCoordinatorId(),
                dto.interviewCoordinatorDepartmentId());

        InterviewRequest request = InterviewRequest.builder()
                .candidateName(candidateName)
                .candidateInviteEmail(candidateInviteEmail)
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

        String interviewType = interviewTypeService.resolveCode(dto.interviewType());

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

        calendarSyncService.afterSingleInterviewBooked(originalSlot, bookedSlot, saved, schedule);

        if (candidate != null) {
            applyCandidateStatusForScheduledInterview(candidate, interviewType, requestedBy);
        }

        try {
            notificationService.sendInterviewScheduledNotification(saved);
            notificationService.sendCoordinatedHrInterviewScheduledNotification(saved);
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

    /**
     * Checks each selected interviewer's connected Google Calendar for events that
     * overlap the proposed interview window. Interviewers without Google Calendar
     * connected (or with no overlap) simply return no conflicts. All-day events are
     * ignored since they don't block a specific meeting time. Times are UTC.
     *
     * <p>Used by HR scheduling to block booking when an overlapping event exists.
     */
    @Transactional(readOnly = true)
    public List<InterviewerConflictsDto> findSchedulingConflicts(
            List<Long> interviewerIds,
            LocalDateTime utcStart,
            LocalDateTime utcEnd) {
        if (interviewerIds == null || interviewerIds.isEmpty()
                || utcStart == null || utcEnd == null || !utcEnd.isAfter(utcStart)) {
            return List.of();
        }

        List<InterviewerConflictsDto> result = new java.util.ArrayList<>();
        for (Long interviewerId : interviewerIds.stream().filter(Objects::nonNull).distinct().toList()) {
            User interviewer = userRepository.findById(interviewerId).orElse(null);
            if (interviewer == null) {
                continue;
            }

            List<GoogleCalendarExternalEventDto> conflicts = calendarSyncService
                    .listExternalGoogleCalendarEvents(interviewer, utcStart, utcEnd)
                    .stream()
                    .filter(event -> !event.allDay())
                    .filter(event -> event.startDateTime() != null && event.endDateTime() != null)
                    // strict overlap: event starts before window ends AND ends after window starts
                    .filter(event -> event.startDateTime().isBefore(utcEnd)
                            && event.endDateTime().isAfter(utcStart))
                    .toList();

            if (!conflicts.isEmpty()) {
                result.add(new InterviewerConflictsDto(
                        interviewer.getId(),
                        interviewer.getFullName(),
                        conflicts));
            }
        }
        return result;
    }

    /**
     * Throws if any selected interviewer has a timed Google Calendar event overlapping
     * the proposed interview window on their configured calendars.
     */
    public void assertNoSchedulingConflicts(
            List<Long> interviewerIds,
            LocalDateTime utcStart,
            LocalDateTime utcEnd) {
        List<InterviewerConflictsDto> conflicts = findSchedulingConflicts(interviewerIds, utcStart, utcEnd);
        if (conflicts.isEmpty()) {
            return;
        }

        String detail = conflicts.stream()
                .map(ic -> {
                    String events = ic.conflicts().stream()
                            .map(event -> {
                                String title = event.title() != null && !event.title().isBlank()
                                        ? event.title()
                                        : "Untitled event";
                                String calendar = event.calendarName() != null && !event.calendarName().isBlank()
                                        ? " (" + event.calendarName() + ")"
                                        : "";
                                return "\"" + title + "\"" + calendar;
                            })
                            .collect(Collectors.joining(", "));
                    return ic.interviewerName() + ": " + events;
                })
                .collect(Collectors.joining("; "));

        throw new RuntimeException("Cannot schedule: Google Calendar conflict — " + detail);
    }

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
        cancelRequest(user, requestId, false);
    }

    /**
     * Cancels a single-interviewer interview. When {@code forReschedule} is true,
     * skip cancel notifications and candidate pipeline reset — the caller will
     * immediately book a replacement interview.
     */
    @Transactional
    public void cancelRequest(User user, Long requestId, boolean forReschedule) {
        boolean isHrOrAdmin = user.getRoles().contains(Role.HR) || user.getRoles().contains(Role.ADMIN);
        if (!isHrOrAdmin) {
            throw new RuntimeException("Only HR or Admin users can cancel interview requests");
        }

        logger.info("HR user {} cancelling request {} (forReschedule={})", user.getId(), requestId, forReschedule);

        InterviewRequest request = interviewRequestRepository.findById(requestId)
                .orElseThrow(() -> new RuntimeException("Request not found: " + requestId));

        if (request.getStatus() == RequestStatus.CANCELLED) {
            throw new RuntimeException("Request is already cancelled");
        }

        // Panel bookings must be cancelled as a unit so every interviewer slot is restored.
        if (request.getPanel() != null) {
            if (forReschedule) {
                throw new RuntimeException("Panel interviews cannot be rescheduled via postpone approval");
            }
            Long panelId = request.getPanel().getId();
            logger.info("Request {} belongs to panel {} — cancelling entire panel", requestId, panelId);
            panelInterviewService.cancelPanelInterview(user, panelId);
            return;
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

        InterviewSchedule schedule = interviewScheduleRepository.findActiveByRequestId(requestId).orElse(null);

        // ── Step 2: Cancel the InterviewSchedule ─────────────────────────────
        if (schedule != null) {
            logger.info("Cancelling InterviewSchedule {}", schedule.getId());
            schedule.setStatus(InterviewStatus.CANCELLED);
            schedule.setMeetingLink(null);
            interviewScheduleRepository.save(schedule);
        }

        if (slot != null) {
            calendarSyncService.cancelSingleInterview(request, schedule, slot);
        }

        // ── Step 3: Mark request CANCELLED ───────────────────────────────────
        request.setStatus(RequestStatus.CANCELLED);
        request.setAvailabilitySlot(null);
        interviewRequestRepository.save(request);
        logger.info("Request {} marked CANCELLED", requestId);

        if (forReschedule) {
            return;
        }

        // ── Step 4: Notify interviewer ────────────────────────────────────────
        try {
            InterviewRequest forNotification = interviewRequestRepository.findById(requestId).orElse(request);
            notificationService.sendInterviewCancelledNotification(forNotification);
            notificationService.sendCoordinatedHrInterviewCancelledNotification(forNotification);
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
                String interviewType = interviewScheduleRepository.findActiveByRequestId(requestId)
                        .map(InterviewSchedule::getInterviewType)
                        .orElse(InterviewTypeService.DEFAULT_CODE);
                String resetStatusKey = interviewTypeService.cancelRestoreStatusKey(interviewType);
                String previousStatusKey = candidate.getMasterStep() != null
                        ? candidate.getMasterStep().getStatusKey()
                        : null;
                masterStepService.assignByStatusKey(candidate, resetStatusKey);
                candidateRepository.save(candidate);
                candidateStepPipelineService.restorePipelineAfterInterviewCancel(
                        candidate.getId(), interviewType);
                candidatePipelineAuditService.recordStatusChange(
                        candidate.getId(),
                        resetStatusKey,
                        previousStatusKey,
                        PipelineAuditActionType.INTERVIEW_CANCELLED,
                        user,
                        "Interview cancelled");
                logger.info("Candidate {} reset to {} after {} interview cancel",
                        candidate.getId(), resetStatusKey, interviewType);
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

        Candidate candidate = request.getCandidate();
        if (candidate != null) {
            String interviewType = schedule.getInterviewType() != null
                    ? schedule.getInterviewType()
                    : InterviewTypeService.DEFAULT_CODE;
            candidateStepPipelineService.completeInterviewRoundStep(candidate.getId(), interviewType);
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
        return new HashSet<>(interviewRequestRepository.findByPanelIdWithDetails(panelId));
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
        return interviewScheduleRepository.findActiveByRequestId(request.getId()).orElse(null);
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    private void applyCandidateStatusForScheduledInterview(Candidate candidate,
                                                           String interviewType,
                                                           User changedBy) {
        String targetStatusKey = interviewTypeService.roundStatusKey(interviewType);
        String oldStatusKey = candidate.getMasterStep() != null
                ? candidate.getMasterStep().getStatusKey()
                : null;
        masterStepService.assignByStatusKey(candidate, targetStatusKey);
        candidateRepository.save(candidate);
        candidateStepPipelineService.updatePipelineOnStatusChange(
                candidate.getId(), targetStatusKey, oldStatusKey, true);
        candidatePipelineAuditService.recordStatusChange(
                candidate.getId(),
                targetStatusKey,
                oldStatusKey,
                PipelineAuditActionType.INTERVIEW_SCHEDULED,
                changedBy,
                interviewType + " interview scheduled");
    }

    private void mergeAdjacentSlots(AvailabilitySlot restoredSlot) {
        Long interviewerId = restoredSlot.getInterviewer().getId();
        LocalDateTime mergedStart = restoredSlot.getStartDateTime();
        LocalDateTime mergedEnd   = restoredSlot.getEndDateTime();
        boolean changed = false;

        List<AvailabilitySlot> beforeSlots = availabilitySlotRepository
                .findActiveAvailableSlotsEndingAt(interviewerId, mergedStart);
        if (!beforeSlots.isEmpty()) {
            AvailabilitySlot before = beforeSlots.get(0);
            logger.info("Merging before-fragment slot {} into restored slot {}",
                    before.getId(), restoredSlot.getId());
            mergedStart = before.getStartDateTime();
            before.setActive(false);
            availabilitySlotRepository.save(before);
            deactivateExtraSlots(beforeSlots, before);
            changed = true;
        }

        List<AvailabilitySlot> afterSlots = availabilitySlotRepository
                .findActiveAvailableSlotsStartingAt(interviewerId, mergedEnd);
        if (!afterSlots.isEmpty()) {
            AvailabilitySlot after = afterSlots.get(0);
            logger.info("Merging after-fragment slot {} into restored slot {}",
                    after.getId(), restoredSlot.getId());
            mergedEnd = after.getEndDateTime();
            after.setActive(false);
            availabilitySlotRepository.save(after);
            deactivateExtraSlots(afterSlots, after);
            changed = true;
        }

        if (changed) {
            restoredSlot.setStartDateTime(mergedStart);
            restoredSlot.setEndDateTime(mergedEnd);
            availabilitySlotRepository.save(restoredSlot);
            logger.info("Slot {} merged to window {} – {}", restoredSlot.getId(), mergedStart, mergedEnd);
        }
    }

    private void deactivateExtraSlots(List<AvailabilitySlot> slots, AvailabilitySlot keep) {
        for (AvailabilitySlot slot : slots) {
            if (!slot.getId().equals(keep.getId())) {
                slot.setActive(false);
                availabilitySlotRepository.save(slot);
            }
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

    private String resolveCandidateInviteEmail(String dtoEmail, Candidate candidate) {
        if (dtoEmail != null && !dtoEmail.isBlank()) {
            return dtoEmail.trim();
        }
        if (candidate != null && candidate.getEmail() != null && !candidate.getEmail().isBlank()) {
            return candidate.getEmail().trim();
        }
        return null;
    }
}