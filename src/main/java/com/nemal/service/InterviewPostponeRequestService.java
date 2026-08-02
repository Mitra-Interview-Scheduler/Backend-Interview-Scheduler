package com.nemal.service;

import com.nemal.dto.ApproveInterviewPostponeRequestDto;
import com.nemal.dto.CreateInterviewPostponeRequestDto;
import com.nemal.dto.CreateInterviewRequestDto;
import com.nemal.dto.CreatePanelInterviewDto;
import com.nemal.dto.InterviewPostponeRequestDto;
import com.nemal.dto.InterviewRequestDto;
import com.nemal.dto.PanelCommonFreeWindowDto;
import com.nemal.dto.RejectInterviewPostponeRequestDto;
import com.nemal.entity.AvailabilitySlot;
import com.nemal.entity.InterviewPanel;
import com.nemal.entity.InterviewPostponeRequest;
import com.nemal.entity.InterviewRequest;
import com.nemal.entity.InterviewSchedule;
import com.nemal.entity.Technology;
import com.nemal.entity.User;
import com.nemal.enums.InterviewStatus;
import com.nemal.enums.PostponeRequestStatus;
import com.nemal.enums.RequestStatus;
import com.nemal.enums.Role;
import com.nemal.enums.SlotStatus;
import com.nemal.repository.AvailabilitySlotRepository;
import com.nemal.repository.InterviewPostponeRequestRepository;
import com.nemal.repository.InterviewRequestRepository;
import com.nemal.repository.InterviewScheduleRepository;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class InterviewPostponeRequestService {

    private static final long MIN_COMMON_WINDOW_MINUTES = 30;

    private final InterviewPostponeRequestRepository postponeRequestRepository;
    private final InterviewScheduleRepository interviewScheduleRepository;
    private final AvailabilitySlotRepository availabilitySlotRepository;
    private final InterviewRequestRepository interviewRequestRepository;
    private final InterviewRequestService interviewRequestService;
    private final PanelInterviewService panelInterviewService;
    private final NotificationService notificationService;

    public InterviewPostponeRequestService(
            InterviewPostponeRequestRepository postponeRequestRepository,
            InterviewScheduleRepository interviewScheduleRepository,
            AvailabilitySlotRepository availabilitySlotRepository,
            InterviewRequestRepository interviewRequestRepository,
            InterviewRequestService interviewRequestService,
            @Lazy PanelInterviewService panelInterviewService,
            NotificationService notificationService) {
        this.postponeRequestRepository = postponeRequestRepository;
        this.interviewScheduleRepository = interviewScheduleRepository;
        this.availabilitySlotRepository = availabilitySlotRepository;
        this.interviewRequestRepository = interviewRequestRepository;
        this.interviewRequestService = interviewRequestService;
        this.panelInterviewService = panelInterviewService;
        this.notificationService = notificationService;
    }

    @Transactional
    public InterviewPostponeRequestDto createPostponeRequest(
            User interviewer,
            Long scheduleId,
            CreateInterviewPostponeRequestDto dto) {
        InterviewSchedule schedule = interviewScheduleRepository.findByIdWithPostponeDetails(scheduleId)
                .orElseThrow(() -> new RuntimeException("Interview schedule not found: " + scheduleId));

        assertInterviewerCanRequest(interviewer, schedule);
        assertScheduleCanBePostponed(schedule);

        InterviewRequest request = schedule.getRequest();
        if (request == null) {
            throw new RuntimeException("Interview request not found for schedule: " + scheduleId);
        }

        InterviewPanel panel = request.getPanel();
        assertNoPendingPostpone(schedule, panel);

        boolean hasPreferredTimes = dto.preferredStartDateTime() != null && dto.preferredEndDateTime() != null;
        boolean missingOneTime = (dto.preferredStartDateTime() == null) != (dto.preferredEndDateTime() == null);
        if (missingOneTime) {
            throw new RuntimeException("Provide both preferred start and end times, or leave both empty");
        }

        String reason = dto.reason() != null ? dto.reason().trim() : "";
        if (reason.length() > 2000) {
            throw new RuntimeException("Reason must be at most 2000 characters");
        }

        if (hasPreferredTimes) {
            validatePreferredWindow(dto.preferredStartDateTime(), dto.preferredEndDateTime());
            if (!dto.preferredStartDateTime().isAfter(LocalDateTime.now())) {
                throw new RuntimeException("The proposed time must be in the future");
            }
            if (panel != null) {
                assertPanelMembersCoverWindow(panel, dto.preferredStartDateTime(), dto.preferredEndDateTime());
            }
            if (reason.isBlank()) {
                reason = panel != null
                        ? "Panel interviewer proposed an alternative time for this panel interview."
                        : "Interviewer proposed an alternative time for this scheduled interview.";
            }
        } else {
            if (panel == null) {
                throw new RuntimeException("A proposed start and end time are required");
            }
            if (reason.isBlank()) {
                throw new RuntimeException("Please explain why you need this panel interview postponed");
            }
        }

        InterviewPostponeRequest postponeRequest = InterviewPostponeRequest.builder()
                .interviewSchedule(schedule)
                .interviewRequest(request)
                .requestedBy(interviewer)
                .reason(reason)
                .preferredStartDateTime(hasPreferredTimes ? dto.preferredStartDateTime() : null)
                .preferredEndDateTime(hasPreferredTimes ? dto.preferredEndDateTime() : null)
                .status(PostponeRequestStatus.PENDING)
                .build();

        InterviewPostponeRequest saved = postponeRequestRepository.save(postponeRequest);
        InterviewPostponeRequest forNotify = postponeRequestRepository.findByIdWithDetails(saved.getId())
                .orElse(saved);
        notificationService.sendInterviewPostponeRequestedNotification(forNotify);
        return InterviewPostponeRequestDto.from(saved);
    }

    @Transactional(readOnly = true)
    public List<PanelCommonFreeWindowDto> listPanelCommonFreeWindows(User interviewer, Long scheduleId) {
        InterviewSchedule schedule = interviewScheduleRepository.findByIdWithPostponeDetails(scheduleId)
                .orElseThrow(() -> new RuntimeException("Interview schedule not found: " + scheduleId));
        assertInterviewerCanRequest(interviewer, schedule);

        InterviewRequest request = schedule.getRequest();
        if (request == null || request.getPanel() == null) {
            throw new RuntimeException("This interview is not part of a panel");
        }

        long minMinutes = Math.max(
                MIN_COMMON_WINDOW_MINUTES,
                Math.max(1, Duration.between(schedule.getStartDateTime(), schedule.getEndDateTime()).toMinutes()));

        return computePanelCommonFreeWindows(request.getPanel(), minMinutes);
    }

    @Transactional(readOnly = true)
    public InterviewPostponeRequestDto getPendingForSchedule(Long scheduleId) {
        InterviewSchedule schedule = interviewScheduleRepository.findByIdWithPostponeDetails(scheduleId)
                .orElse(null);
        if (schedule == null) {
            return null;
        }

        InterviewPanel panel = schedule.getRequest() != null ? schedule.getRequest().getPanel() : null;
        if (panel != null) {
            return findPendingForPanel(panel.getId()).map(InterviewPostponeRequestDto::from).orElse(null);
        }

        return postponeRequestRepository
                .findPendingByScheduleIdWithDetails(scheduleId, PostponeRequestStatus.PENDING)
                .map(InterviewPostponeRequestDto::from)
                .orElse(null);
    }

    @Transactional(readOnly = true)
    public List<InterviewPostponeRequestDto> getHistoryForSchedule(Long scheduleId) {
        return postponeRequestRepository.findByInterviewScheduleIdOrderByCreatedAtDesc(scheduleId)
                .stream()
                .map(InterviewPostponeRequestDto::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<InterviewPostponeRequestDto> listPendingRequests() {
        return postponeRequestRepository.findByStatusOrderByCreatedAtDesc(PostponeRequestStatus.PENDING)
                .stream()
                .map(InterviewPostponeRequestDto::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public long countPendingRequests() {
        return postponeRequestRepository.countByStatus(PostponeRequestStatus.PENDING);
    }

    @Transactional(readOnly = true)
    public Map<Long, InterviewPostponeRequestDto> findPendingByScheduleIds(Collection<Long> scheduleIds) {
        if (scheduleIds == null || scheduleIds.isEmpty()) {
            return Map.of();
        }

        List<InterviewPostponeRequest> pending = postponeRequestRepository
                .findByInterviewScheduleIdInAndStatus(scheduleIds, PostponeRequestStatus.PENDING);

        Map<Long, InterviewPostponeRequestDto> bySchedule = new HashMap<>();
        Set<Long> panelIdsNeedingLookup = new HashSet<>();

        for (InterviewPostponeRequest request : pending) {
            if (request.getInterviewSchedule() == null) {
                continue;
            }
            Long scheduleId = request.getInterviewSchedule().getId();
            InterviewPostponeRequestDto dto = InterviewPostponeRequestDto.from(request);
            bySchedule.put(scheduleId, dto);

            InterviewRequest interviewRequest = request.getInterviewRequest();
            if (interviewRequest == null) {
                interviewRequest = request.getInterviewSchedule().getRequest();
            }
            if (interviewRequest != null && interviewRequest.getPanel() != null) {
                panelIdsNeedingLookup.add(interviewRequest.getPanel().getId());
            }
        }

        // Also surface pending requests that belong to the same panel but were
        // filed against a sibling schedule not present in the original id set.
        Set<Long> remainingScheduleIds = scheduleIds.stream()
                .filter(id -> !bySchedule.containsKey(id))
                .collect(Collectors.toSet());
        if (!remainingScheduleIds.isEmpty()) {
            for (Long scheduleId : remainingScheduleIds) {
                InterviewSchedule schedule = interviewScheduleRepository.findByIdWithPostponeDetails(scheduleId)
                        .orElse(null);
                if (schedule == null || schedule.getRequest() == null || schedule.getRequest().getPanel() == null) {
                    continue;
                }
                Long panelId = schedule.getRequest().getPanel().getId();
                findPendingForPanel(panelId).ifPresent(pendingRequest ->
                        bySchedule.put(scheduleId, InterviewPostponeRequestDto.from(pendingRequest)));
            }
        }

        // Expand known panel pending rows onto every schedule id in that panel
        // that appears in the requested set.
        for (Long panelId : panelIdsNeedingLookup) {
            InterviewPostponeRequestDto panelPending = findPendingForPanel(panelId)
                    .map(InterviewPostponeRequestDto::from)
                    .orElse(null);
            if (panelPending == null) {
                continue;
            }
            for (InterviewSchedule panelSchedule : interviewScheduleRepository.findByPanelId(panelId)) {
                if (scheduleIds.contains(panelSchedule.getId())) {
                    bySchedule.putIfAbsent(panelSchedule.getId(), panelPending);
                }
            }
        }

        return bySchedule;
    }

    @Transactional
    public InterviewPostponeRequestDto withdrawPostponeRequest(User interviewer, Long postponeRequestId) {
        InterviewPostponeRequest request = postponeRequestRepository.findByIdWithDetails(postponeRequestId)
                .orElseThrow(() -> new RuntimeException("Postpone request not found: " + postponeRequestId));

        if (request.getStatus() != PostponeRequestStatus.PENDING) {
            throw new RuntimeException("Only pending postpone requests can be withdrawn");
        }
        if (request.getRequestedBy() == null
                || !Objects.equals(request.getRequestedBy().getId(), interviewer.getId())) {
            throw new RuntimeException("You can only withdraw your own postpone request");
        }

        request.setStatus(PostponeRequestStatus.WITHDRAWN);
        request.setResolvedAt(LocalDateTime.now());
        InterviewPostponeRequest saved = postponeRequestRepository.save(request);
        return InterviewPostponeRequestDto.from(saved);
    }

    @Transactional
    public InterviewPostponeRequestDto rejectPostponeRequest(
            User hrUser,
            Long postponeRequestId,
            RejectInterviewPostponeRequestDto dto) {
        assertHrOrAdmin(hrUser);

        InterviewPostponeRequest request = postponeRequestRepository.findByIdWithDetails(postponeRequestId)
                .orElseThrow(() -> new RuntimeException("Postpone request not found: " + postponeRequestId));

        if (request.getStatus() != PostponeRequestStatus.PENDING) {
            throw new RuntimeException("Only pending postpone requests can be rejected");
        }

        request.setStatus(PostponeRequestStatus.REJECTED);
        request.setReviewedBy(hrUser);
        request.setReviewNotes(dto != null && dto.reviewNotes() != null ? dto.reviewNotes().trim() : null);
        request.setResolvedAt(LocalDateTime.now());

        InterviewPostponeRequest saved = postponeRequestRepository.save(request);
        notificationService.sendInterviewPostponeRejectedNotification(saved);
        return InterviewPostponeRequestDto.from(saved);
    }

    /**
     * Approves a pending postpone.
     * <ul>
     *   <li>1:1 with times — cancel and rebook at proposed time</li>
     *   <li>Panel with times — cancel whole panel and recreate at proposed time</li>
     *   <li>Panel without times — acknowledge only; booking stays until HR reschedules</li>
     * </ul>
     */
    @Transactional
    public InterviewPostponeRequestDto approvePostponeRequest(
            User hrUser,
            Long postponeRequestId,
            ApproveInterviewPostponeRequestDto dto) {
        assertHrOrAdmin(hrUser);

        InterviewPostponeRequest postpone = postponeRequestRepository.findByIdWithDetails(postponeRequestId)
                .orElseThrow(() -> new RuntimeException("Postpone request not found: " + postponeRequestId));

        if (postpone.getStatus() != PostponeRequestStatus.PENDING) {
            throw new RuntimeException("Only pending postpone requests can be approved");
        }

        InterviewSchedule oldSchedule = postpone.getInterviewSchedule();
        InterviewRequest oldRequest = postpone.getInterviewRequest();
        if (oldRequest == null && oldSchedule != null) {
            oldRequest = oldSchedule.getRequest();
        }
        if (oldSchedule == null || oldRequest == null) {
            throw new RuntimeException("Postpone request is missing interview details");
        }
        if (oldSchedule.getStatus() != InterviewStatus.SCHEDULED) {
            throw new RuntimeException("The original interview is no longer scheduled");
        }

        InterviewPanel panel = oldRequest.getPanel();
        LocalDateTime preferredStart = postpone.getPreferredStartDateTime();
        LocalDateTime preferredEnd = postpone.getPreferredEndDateTime();
        boolean hasPreferredTimes = preferredStart != null && preferredEnd != null;

        postpone.setStatus(PostponeRequestStatus.APPROVED);
        postpone.setReviewedBy(hrUser);
        postpone.setReviewNotes(dto != null && dto.reviewNotes() != null ? dto.reviewNotes().trim() : null);
        postpone.setResolvedAt(LocalDateTime.now());
        postponeRequestRepository.save(postpone);

        if (panel != null && !hasPreferredTimes) {
            InterviewPostponeRequest saved = postponeRequestRepository.findByIdWithDetails(postponeRequestId)
                    .orElse(postpone);
            try {
                notificationService.sendInterviewPostponeAcknowledgedNotification(saved);
            } catch (Exception ignored) {
                // acknowledgement already persisted
            }
            return InterviewPostponeRequestDto.from(saved);
        }

        if (!hasPreferredTimes) {
            throw new RuntimeException("This postpone request has no proposed time to accept");
        }

        validatePreferredWindow(preferredStart, preferredEnd);
        if (!preferredStart.isAfter(LocalDateTime.now())) {
            throw new RuntimeException("The proposed time is in the past and cannot be accepted");
        }

        if (panel != null) {
            return approvePanelPostpone(hrUser, postpone, oldRequest, panel, preferredStart, preferredEnd, dto);
        }

        return approveSinglePostpone(hrUser, postpone, oldRequest, oldSchedule, preferredStart, preferredEnd, dto);
    }

    private InterviewPostponeRequestDto approveSinglePostpone(
            User hrUser,
            InterviewPostponeRequest postpone,
            InterviewRequest oldRequest,
            InterviewSchedule oldSchedule,
            LocalDateTime preferredStart,
            LocalDateTime preferredEnd,
            ApproveInterviewPostponeRequestDto dto) {
        User interviewer = oldSchedule.getInterviewer() != null
                ? oldSchedule.getInterviewer()
                : oldRequest.getAssignedInterviewer();
        if (interviewer == null || interviewer.getId() == null) {
            throw new RuntimeException("Interviewer not found for this interview");
        }

        List<AvailabilitySlot> covering = availabilitySlotRepository.findCoveringAvailableSlots(
                interviewer.getId(), preferredStart, preferredEnd);
        if (covering.isEmpty()) {
            throw new RuntimeException(
                    "No available slot covers the proposed time. Ask the interviewer to propose another slot.");
        }
        AvailabilitySlot targetSlot = covering.get(0);

        List<Long> technologyIds = oldRequest.getRequiredTechnologies() == null
                ? List.of()
                : oldRequest.getRequiredTechnologies().stream()
                        .filter(Objects::nonNull)
                        .map(Technology::getId)
                        .filter(Objects::nonNull)
                        .toList();

        Long coordinatorId = oldRequest.getInterviewCoordinator() != null
                ? oldRequest.getInterviewCoordinator().getId()
                : null;

        String notes = "Rescheduled after accepting postpone request #" + postpone.getId();
        if (oldRequest.getNotes() != null && !oldRequest.getNotes().isBlank()) {
            notes = oldRequest.getNotes().trim() + " | " + notes;
        }

        CreateInterviewRequestDto createDto = new CreateInterviewRequestDto(
                oldRequest.getCandidate() != null ? oldRequest.getCandidate().getId() : null,
                oldRequest.getCandidateName(),
                oldRequest.getCandidateInviteEmail(),
                oldRequest.getCandidateDesignation() != null
                        ? oldRequest.getCandidateDesignation().getId()
                        : null,
                technologyIds,
                targetSlot.getId(),
                preferredStart,
                preferredEnd,
                oldRequest.isUrgent(),
                notes,
                oldSchedule.getInterviewType(),
                coordinatorId,
                null,
                dto != null ? dto.acknowledgeCalendarConflict() : null
        );

        interviewRequestService.cancelRequest(hrUser, oldRequest.getId(), true);
        InterviewRequestDto newInterview = interviewRequestService.createInterviewRequest(hrUser, createDto);

        InterviewPostponeRequest saved = postponeRequestRepository.findByIdWithDetails(postpone.getId())
                .orElse(postpone);
        try {
            notificationService.sendInterviewPostponeApprovedNotification(saved, newInterview);
        } catch (Exception ignored) {
            // booking already succeeded
        }
        return InterviewPostponeRequestDto.from(saved);
    }

    private InterviewPostponeRequestDto approvePanelPostpone(
            User hrUser,
            InterviewPostponeRequest postpone,
            InterviewRequest oldRequest,
            InterviewPanel panel,
            LocalDateTime preferredStart,
            LocalDateTime preferredEnd,
            ApproveInterviewPostponeRequestDto dto) {
        List<InterviewRequest> panelRequests = interviewRequestRepository.findByPanelIdWithDetails(panel.getId())
                .stream()
                .filter(r -> r.getStatus() == RequestStatus.ACCEPTED)
                .toList();
        if (panelRequests.isEmpty()) {
            throw new RuntimeException("Panel has no active interviewers to reschedule");
        }

        List<User> interviewers = panelRequests.stream()
                .map(InterviewRequest::getAssignedInterviewer)
                .filter(Objects::nonNull)
                .toList();
        for (User interviewer : interviewers) {
            if (availabilitySlotRepository.findCoveringAvailableSlots(
                    interviewer.getId(), preferredStart, preferredEnd).isEmpty()) {
                throw new RuntimeException(
                        "No available slot covers the proposed time for " + interviewer.getFullName());
            }
        }

        String interviewType = interviewScheduleRepository.findByPanelId(panel.getId()).stream()
                .map(InterviewSchedule::getInterviewType)
                .filter(Objects::nonNull)
                .findFirst()
                .orElse(null);

        List<Long> technologyIds = oldRequest.getRequiredTechnologies() == null
                ? List.of()
                : oldRequest.getRequiredTechnologies().stream()
                        .filter(Objects::nonNull)
                        .map(Technology::getId)
                        .filter(Objects::nonNull)
                        .toList();

        Long coordinatorId = panel.getInterviewCoordinator() != null
                ? panel.getInterviewCoordinator().getId()
                : (oldRequest.getInterviewCoordinator() != null
                        ? oldRequest.getInterviewCoordinator().getId()
                        : null);

        String notes = "Rescheduled after accepting panel postpone request #" + postpone.getId();
        if (oldRequest.getNotes() != null && !oldRequest.getNotes().isBlank()) {
            notes = oldRequest.getNotes().trim() + " | " + notes;
        }

        Long panelId = panel.getId();
        Long candidateId = panel.getCandidate() != null ? panel.getCandidate().getId() : null;
        String candidateName = panel.getCandidateName() != null
                ? panel.getCandidateName()
                : oldRequest.getCandidateName();
        String candidateEmail = panel.getCandidateInviteEmail() != null
                ? panel.getCandidateInviteEmail()
                : oldRequest.getCandidateInviteEmail();
        Long designationId = oldRequest.getCandidateDesignation() != null
                ? oldRequest.getCandidateDesignation().getId()
                : null;
        boolean urgent = panel.isUrgent() || oldRequest.isUrgent();
        List<Long> interviewerIds = interviewers.stream().map(User::getId).toList();

        panelInterviewService.cancelPanelInterview(hrUser, panelId, true);

        List<Long> slotIds = new ArrayList<>();
        for (Long interviewerId : interviewerIds) {
            List<AvailabilitySlot> covering = availabilitySlotRepository.findCoveringAvailableSlots(
                    interviewerId, preferredStart, preferredEnd);
            if (covering.isEmpty()) {
                throw new RuntimeException(
                        "After cancelling the panel, no available slot covers the proposed time for interviewer "
                                + interviewerId);
            }
            slotIds.add(covering.get(0).getId());
        }

        CreatePanelInterviewDto createDto = new CreatePanelInterviewDto(
                candidateId,
                candidateName,
                candidateEmail,
                designationId,
                preferredStart,
                preferredEnd,
                slotIds,
                technologyIds,
                urgent,
                notes,
                interviewType,
                coordinatorId,
                null,
                dto != null ? dto.acknowledgeCalendarConflict() : null
        );

        panelInterviewService.createPanelInterview(hrUser, createDto);

        InterviewPostponeRequest saved = postponeRequestRepository.findByIdWithDetails(postpone.getId())
                .orElse(postpone);
        try {
            notificationService.sendInterviewPostponeApprovedNotification(saved, null);
        } catch (Exception ignored) {
            // booking already succeeded
        }
        return InterviewPostponeRequestDto.from(saved);
    }

    private void assertNoPendingPostpone(InterviewSchedule schedule, InterviewPanel panel) {
        if (panel != null) {
            if (findPendingForPanel(panel.getId()).isPresent()) {
                throw new RuntimeException("A postpone request is already pending for this panel interview");
            }
            return;
        }
        if (postponeRequestRepository.findByInterviewScheduleIdAndStatus(
                schedule.getId(), PostponeRequestStatus.PENDING).isPresent()) {
            throw new RuntimeException("A postpone request is already pending for this interview");
        }
    }

    private java.util.Optional<InterviewPostponeRequest> findPendingForPanel(Long panelId) {
        List<Long> scheduleIds = interviewScheduleRepository.findByPanelId(panelId).stream()
                .map(InterviewSchedule::getId)
                .filter(Objects::nonNull)
                .toList();
        if (scheduleIds.isEmpty()) {
            return java.util.Optional.empty();
        }
        return postponeRequestRepository
                .findByInterviewScheduleIdInAndStatus(scheduleIds, PostponeRequestStatus.PENDING)
                .stream()
                .findFirst();
    }

    private void assertPanelMembersCoverWindow(
            InterviewPanel panel,
            LocalDateTime preferredStart,
            LocalDateTime preferredEnd) {
        List<InterviewRequest> panelRequests = interviewRequestRepository.findByPanelIdWithDetails(panel.getId())
                .stream()
                .filter(r -> r.getStatus() == RequestStatus.ACCEPTED)
                .toList();
        if (panelRequests.isEmpty()) {
            throw new RuntimeException("Panel has no active interviewers");
        }
        for (InterviewRequest panelRequest : panelRequests) {
            User interviewer = panelRequest.getAssignedInterviewer();
            if (interviewer == null) {
                throw new RuntimeException("Panel interviewer is missing");
            }
            if (availabilitySlotRepository.findCoveringAvailableSlots(
                    interviewer.getId(), preferredStart, preferredEnd).isEmpty()) {
                throw new RuntimeException(
                        "Proposed time is not free for all panel members ("
                                + interviewer.getFullName() + " is unavailable)");
            }
        }
    }

    private List<PanelCommonFreeWindowDto> computePanelCommonFreeWindows(
            InterviewPanel panel,
            long minDurationMinutes) {
        List<InterviewRequest> panelRequests = interviewRequestRepository.findByPanelIdWithDetails(panel.getId())
                .stream()
                .filter(r -> r.getStatus() == RequestStatus.ACCEPTED)
                .toList();
        if (panelRequests.isEmpty()) {
            return List.of();
        }

        LocalDateTime now = LocalDateTime.now();
        LocalDateTime horizon = now.plusDays(60);
        long minDurationMs = minDurationMinutes * 60_000L;

        List<List<Interval>> perInterviewer = new ArrayList<>();
        List<String> names = new ArrayList<>();

        for (InterviewRequest panelRequest : panelRequests) {
            User interviewer = panelRequest.getAssignedInterviewer();
            if (interviewer == null) {
                return List.of();
            }
            names.add(interviewer.getFullName());
            List<AvailabilitySlot> slots = availabilitySlotRepository
                    .findActiveSlotsForInterviewerBetween(interviewer.getId(), now, horizon)
                    .stream()
                    .filter(s -> s.getStatus() == SlotStatus.AVAILABLE)
                    .filter(s -> s.getEndDateTime().isAfter(now))
                    .toList();

            List<Interval> intervals = new ArrayList<>();
            for (AvailabilitySlot slot : slots) {
                LocalDateTime start = slot.getStartDateTime().isBefore(now) ? now : slot.getStartDateTime();
                LocalDateTime end = slot.getEndDateTime();
                if (end.isAfter(start)) {
                    intervals.add(new Interval(start, end, slot.getId(), interviewer.getFullName()));
                }
            }
            if (intervals.isEmpty()) {
                return List.of();
            }
            perInterviewer.add(intervals);
        }

        List<CommonWindow> current = perInterviewer.get(0).stream()
                .map(interval -> new CommonWindow(
                        interval.start,
                        interval.end,
                        new ArrayList<>(List.of(interval.slotId)),
                        new ArrayList<>(List.of(interval.interviewerName))))
                .collect(Collectors.toCollection(ArrayList::new));

        for (int i = 1; i < perInterviewer.size(); i++) {
            List<Interval> nextList = perInterviewer.get(i);
            List<CommonWindow> next = new ArrayList<>();
            for (CommonWindow window : current) {
                for (Interval item : nextList) {
                    LocalDateTime start = window.start.isAfter(item.start) ? window.start : item.start;
                    LocalDateTime end = window.end.isBefore(item.end) ? window.end : item.end;
                    if (Duration.between(start, end).toMillis() >= minDurationMs) {
                        List<Long> slotIds = new ArrayList<>(window.slotIds);
                        slotIds.add(item.slotId);
                        List<String> interviewerNames = new ArrayList<>(window.interviewerNames);
                        interviewerNames.add(item.interviewerName);
                        next.add(new CommonWindow(start, end, slotIds, interviewerNames));
                    }
                }
            }
            current = next;
            if (current.isEmpty()) {
                break;
            }
        }

        return current.stream()
                .sorted(Comparator.comparing(w -> w.start))
                .map(w -> new PanelCommonFreeWindowDto(
                        w.start,
                        w.end,
                        List.copyOf(w.slotIds),
                        List.copyOf(w.interviewerNames)))
                .toList();
    }

    private void assertHrOrAdmin(User user) {
        if (user == null || user.getRoles() == null
                || !(user.getRoles().contains(Role.HR) || user.getRoles().contains(Role.ADMIN))) {
            throw new RuntimeException("Only HR or Admin can review postpone requests");
        }
    }

    private void assertInterviewerCanRequest(User interviewer, InterviewSchedule schedule) {
        if (schedule.getInterviewer() == null
                || !Objects.equals(schedule.getInterviewer().getId(), interviewer.getId())) {
            throw new RuntimeException("Only the assigned interviewer can request to postpone this interview");
        }
    }

    private void assertScheduleCanBePostponed(InterviewSchedule schedule) {
        if (schedule.getStatus() == InterviewStatus.COMPLETED) {
            throw new RuntimeException("Completed interviews cannot be postponed");
        }
        if (schedule.getStatus() == InterviewStatus.CANCELLED) {
            throw new RuntimeException("Cancelled interviews cannot be postponed");
        }
        if (schedule.getStatus() != InterviewStatus.SCHEDULED) {
            throw new RuntimeException("Only scheduled interviews can be postponed");
        }
    }

    private void validatePreferredWindow(LocalDateTime preferredStart, LocalDateTime preferredEnd) {
        if (preferredStart == null || preferredEnd == null) {
            throw new RuntimeException("Both preferred start and end times are required when suggesting a new time");
        }
        if (!preferredEnd.isAfter(preferredStart)) {
            throw new RuntimeException("Preferred end time must be after preferred start time");
        }
    }

    private record Interval(LocalDateTime start, LocalDateTime end, Long slotId, String interviewerName) {}

    private record CommonWindow(
            LocalDateTime start,
            LocalDateTime end,
            List<Long> slotIds,
            List<String> interviewerNames) {}
}
