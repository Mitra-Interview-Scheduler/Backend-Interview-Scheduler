package com.nemal.service;

import com.nemal.dto.CreatePanelInterviewDto;
import com.nemal.dto.InterviewPanelDto;
import com.nemal.entity.*;
import com.nemal.enums.InterviewStatus;
import com.nemal.enums.InterviewType;
import com.nemal.enums.MasterStatus;
import com.nemal.enums.PipelineAuditActionType;
import com.nemal.enums.RequestStatus;
import com.nemal.enums.Role;
import com.nemal.enums.SlotStatus;
import com.nemal.repository.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class PanelInterviewService {

    private static final Logger logger = LoggerFactory.getLogger(PanelInterviewService.class);

    private final InterviewPanelRepository panelRepository;
    private final AvailabilitySlotRepository slotRepository;
    private final InterviewRequestRepository requestRepository;
    private final InterviewScheduleRepository scheduleRepository;
    private final CandidateRepository candidateRepository;
    private final DesignationRepository designationRepository;
    private final TechnologyRepository technologyRepository;
    private final TierRepository tierRepository;
    private final NotificationService notificationService;
    private final CandidateStepPipelineService candidateStepPipelineService;
    private final MasterStepService masterStepService;
    private final UserRepository userRepository;
    private final CandidatePipelineAuditService candidatePipelineAuditService;
    private final CalendarSyncService calendarSyncService;
    private final InterviewRequestService interviewRequestService;

    public PanelInterviewService(
            InterviewPanelRepository panelRepository,
            AvailabilitySlotRepository slotRepository,
            InterviewRequestRepository requestRepository,
            InterviewScheduleRepository scheduleRepository,
            CandidateRepository candidateRepository,
            DesignationRepository designationRepository,
            TechnologyRepository technologyRepository,
            TierRepository tierRepository,
            NotificationService notificationService,
            CandidateStepPipelineService candidateStepPipelineService,
            MasterStepService masterStepService,
            UserRepository userRepository,
            CandidatePipelineAuditService candidatePipelineAuditService,
            CalendarSyncService calendarSyncService,
            @Lazy InterviewRequestService interviewRequestService) {
        this.panelRepository = panelRepository;
        this.slotRepository = slotRepository;
        this.requestRepository = requestRepository;
        this.scheduleRepository = scheduleRepository;
        this.candidateRepository = candidateRepository;
        this.designationRepository = designationRepository;
        this.technologyRepository = technologyRepository;
        this.tierRepository = tierRepository;
        this.notificationService = notificationService;
        this.candidateStepPipelineService = candidateStepPipelineService;
        this.masterStepService = masterStepService;
        this.userRepository = userRepository;
        this.candidatePipelineAuditService = candidatePipelineAuditService;
        this.calendarSyncService = calendarSyncService;
        this.interviewRequestService = interviewRequestService;
    }

    @Transactional
    public InterviewPanelDto createPanelInterview(User requestedBy, CreatePanelInterviewDto dto) {
        logger.info("Creating panel interview: candidate='{}', {} interviewers",
                dto.candidateName(), dto.availabilitySlotIds().size());

        if (dto.availabilitySlotIds() == null || dto.availabilitySlotIds().isEmpty()) {
            throw new RuntimeException("At least one interviewer slot must be selected for a panel");
        }

        Candidate candidate = null;
        if (dto.candidateId() != null) {
            candidate = candidateRepository.findById(dto.candidateId())
                    .orElseThrow(() -> new RuntimeException("Candidate not found"));
        }
        String candidateName = candidate != null ? candidate.getName() : dto.candidateName();
        if (candidateName == null || candidateName.trim().isEmpty()) {
            throw new RuntimeException("Candidate name is required");
        }
        String candidateInviteEmail = resolveCandidateInviteEmail(dto.candidateEmail(), candidate);

        Designation designation = null;
        if (dto.candidateDesignationId() != null) {
            designation = designationRepository.findById(dto.candidateDesignationId())
                    .orElseThrow(() -> new RuntimeException("Designation not found"));
        }

        Set<Technology> technologies = new HashSet<>();
        if (dto.requiredTechnologyIds() != null && !dto.requiredTechnologyIds().isEmpty()) {
            technologies = new HashSet<>(technologyRepository.findAllById(dto.requiredTechnologyIds()));
        }

        List<AvailabilitySlot> slots = dto.availabilitySlotIds().stream()
                .map(slotId -> {
                    AvailabilitySlot slot = slotRepository.findById(slotId)
                            .orElseThrow(() -> new RuntimeException("Slot not found: " + slotId));
                    if (slot.getStatus() != SlotStatus.AVAILABLE) {
                        throw new RuntimeException("Slot for " + slot.getInterviewer().getFullName() + " is no longer available");
                    }
                    if (dto.startDateTime().isBefore(slot.getStartDateTime())) {
                        throw new RuntimeException("Panel start time is before " + slot.getInterviewer().getFullName() + "'s slot start");
                    }
                    if (dto.endDateTime().isAfter(slot.getEndDateTime())) {
                        throw new RuntimeException("Panel end time is after " + slot.getInterviewer().getFullName() + "'s slot end");
                    }
                    return slot;
                })
                .collect(Collectors.toList());

        List<Long> panelInterviewerIds = slots.stream()
                .map(slot -> slot.getInterviewer().getId())
                .distinct()
                .collect(Collectors.toList());
        if (!Boolean.TRUE.equals(dto.acknowledgeCalendarConflict())) {
            interviewRequestService.assertNoSchedulingConflicts(
                    panelInterviewerIds,
                    dto.startDateTime(),
                    dto.endDateTime());
        }

        User interviewCoordinator = resolveInterviewCoordinator(
                dto.interviewCoordinatorId(),
                dto.interviewCoordinatorDepartmentId());

        InterviewPanel panel = InterviewPanel.builder()
                .candidate(candidate)
                .candidateName(candidateName)
                .candidateInviteEmail(candidateInviteEmail)
                .startDateTime(dto.startDateTime())
                .endDateTime(dto.endDateTime())
                .requestedBy(requestedBy)
                .interviewCoordinator(interviewCoordinator)
                .isUrgent(dto.isUrgent())
                .notes(dto.notes())
                .build();
        panel = panelRepository.save(panel);

        Designation finalDesignation = designation;
        String finalCandidateName = candidateName;
        Set<Technology> finalTechnologies = technologies;
        InterviewType interviewType = InterviewType.fromValue(dto.interviewType());

        List<InterviewRequest> createdRequests = new java.util.ArrayList<>();
        List<AvailabilitySlot> bookedSlots = new java.util.ArrayList<>();

        for (AvailabilitySlot slot : slots) {
            AvailabilitySlot bookedSlot = splitSlot(slot, dto.startDateTime(), dto.endDateTime(), finalCandidateName);

            InterviewRequest request = InterviewRequest.builder()
                    .candidateName(finalCandidateName)
                    .candidateInviteEmail(candidateInviteEmail)
                    .candidate(candidate)
                    .candidateDesignation(finalDesignation)
                    .requiredTechnologies(new HashSet<>(finalTechnologies))
                    .preferredStartDateTime(dto.startDateTime())
                    .preferredEndDateTime(dto.endDateTime())
                    .requestedBy(requestedBy)
                    .assignedInterviewer(slot.getInterviewer())
                    .interviewCoordinator(interviewCoordinator)
                    .availabilitySlot(bookedSlot)
                    .panel(panel)
                    .status(RequestStatus.ACCEPTED)
                    .respondedAt(LocalDateTime.now())
                    .isUrgent(dto.isUrgent())
                    .notes(dto.notes())
                    .responseNotes("Auto-accepted as part of panel interview")
                    .build();

            request = requestRepository.save(request);
            createdRequests.add(request);

            InterviewSchedule schedule = InterviewSchedule.builder()
                    .request(request)
                    .interviewer(slot.getInterviewer())
                    .startDateTime(dto.startDateTime())
                    .endDateTime(dto.endDateTime())
                    .status(InterviewStatus.SCHEDULED)
                    .interviewType(interviewType)
                    .build();
            schedule = scheduleRepository.save(schedule);

            bookedSlot.setInterviewSchedule(schedule);
            slotRepository.save(bookedSlot);
            bookedSlots.add(bookedSlot);

            try {
                notificationService.sendInterviewScheduledNotification(request);
            } catch (Exception e) {
                logger.warn("Failed to send scheduled notification to {}: {}", slot.getInterviewer().getFullName(), e.getMessage());
            }
        }

        calendarSyncService.afterPanelInterviewBooked(panel, createdRequests, bookedSlots);

        if (candidate != null) {
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
                    requestedBy,
                    interviewType.name() + " panel interview scheduled");
        }

        InterviewPanel savedPanel = loadPanelWithRequests(panel.getId());

        if (interviewCoordinator != null) {
            try {
                notificationService.sendInterviewCoordinatorPanelScheduledNotification(savedPanel, candidateName);
            } catch (Exception e) {
                logger.warn("Failed to send coordinator panel notification: {}", e.getMessage());
            }
        }

        try {
            notificationService.sendCoordinatedHrPanelInterviewScheduledNotification(savedPanel, candidateName);
        } catch (Exception e) {
            logger.warn("Failed to send coordinated HR panel schedule notification: {}", e.getMessage());
        }

        return InterviewPanelDto.from(savedPanel);
    }

    @Transactional
    public void cancelPanelInterview(User hrUser, Long panelId) {
        InterviewPanel panel = loadPanelWithRequests(panelId);

        boolean isHrOrAdmin = hrUser.getRoles().contains(Role.HR) || hrUser.getRoles().contains(Role.ADMIN);
        boolean isCreator = panel.getRequestedBy() != null
                && panel.getRequestedBy().getId().equals(hrUser.getId());
        if (!isHrOrAdmin && !isCreator) {
            throw new RuntimeException("Unauthorized — only HR or Admin can cancel panel interviews");
        }

        List<InterviewRequest> panelRequests = new java.util.ArrayList<>(panel.getPanelRequests());

        List<InterviewRequest> cancelledRequests = new java.util.ArrayList<>();
        List<AvailabilitySlot> restoredSlots = new java.util.ArrayList<>();

        for (InterviewRequest request : panelRequests) {
            if (request.getStatus() == RequestStatus.CANCELLED) continue;

            AvailabilitySlot slot = request.getAvailabilitySlot();
            if (slot != null) {
                logger.info("Panel cancel: restoring slot {} for interviewer {}",
                        slot.getId(), request.getAssignedInterviewer().getFullName());

                slot.setInterviewSchedule(null);
                slot.setStatus(SlotStatus.AVAILABLE);
                slot.setActive(true);
                slot.setDescription(null);
                slotRepository.save(slot);

                mergeAdjacentSlots(slot);
                restoredSlots.add(slot);
            }

            scheduleRepository.findActiveByRequestId(request.getId()).ifPresent(schedule -> {
                schedule.setStatus(InterviewStatus.CANCELLED);
                scheduleRepository.save(schedule);
            });

            request.setStatus(RequestStatus.CANCELLED);
            request.setAvailabilitySlot(null);
            requestRepository.save(request);
            cancelledRequests.add(request);

            try {
                notificationService.sendInterviewCancelledNotification(request);
            } catch (Exception e) {
                logger.warn("Failed to send cancellation notification: {}", e.getMessage());
            }
        }

        calendarSyncService.cancelPanelInterview(panel, cancelledRequests, restoredSlots);

        try {
            String candidateName = panel.getCandidate() != null
                    ? panel.getCandidate().getName()
                    : "the candidate";
            notificationService.sendCoordinatedHrPanelInterviewCancelledNotification(panel, candidateName);
        } catch (Exception e) {
            logger.warn("Failed to send coordinated HR panel cancellation notification: {}", e.getMessage());
        }

        if (panel.getCandidate() != null) {
            Candidate candidate = panel.getCandidate();
            long activeCount = requestRepository.findByCandidateId(candidate.getId())
                    .stream()
                    .filter(r -> r.getStatus() == RequestStatus.ACCEPTED)
                    .count();
            if (activeCount == 0) {
                InterviewType interviewType = panelRequests.stream()
                        .map(request -> scheduleRepository.findActiveByRequestId(request.getId()).orElse(null))
                        .filter(schedule -> schedule != null && schedule.getInterviewType() != null)
                        .map(InterviewSchedule::getInterviewType)
                        .findFirst()
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
                        hrUser,
                        "Panel interview cancelled");
                logger.info("Candidate {} reset to {} after panel {} cancel",
                        candidate.getId(), resetStatus, panelId);
            }
        }

        logger.info("Cancelled panel {} — all slots restored and merged", panelId);
    }

    // ── Read ──────────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<InterviewPanelDto> getPanelsByCandidateId(Long candidateId) {
        return panelRepository.findByCandidateId(candidateId)
                .stream().map(InterviewPanelDto::from).collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<InterviewPanelDto> getPanelsByRequestedBy(Long userId) {
        return panelRepository.findByRequestedById(userId)
                .stream().map(InterviewPanelDto::from).collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<InterviewPanelDto> getPanelsByRequestedBy(Long userId, Long departmentId, Integer minTierId, Integer exactTierId) {
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
            // ignore
        }
        return panelRepository.findByRequestedByIdWithFilters(userId, departmentId, minTierOrder, exactTierOrder)
                .stream().map(InterviewPanelDto::from).collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<InterviewPanelDto> getPanelsByRequestedBy(Long userId, int limit) {
        int safeLimit = Math.max(1, limit);
        return panelRepository.findByRequestedById(userId)
                .stream()
                .limit(safeLimit)
                .map(InterviewPanelDto::from)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<InterviewPanelDto> getPanelsByRequestedBy(Long userId, int limit, Long departmentId, Integer minTierId, Integer exactTierId) {
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
        }
        return panelRepository.findByRequestedByIdWithFilters(userId, departmentId, minTierOrder, exactTierOrder)
                .stream()
                .limit(safeLimit)
                .map(InterviewPanelDto::from)
                .collect(Collectors.toList());
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    private InterviewPanel loadPanelWithRequests(Long panelId) {
        InterviewPanel panel = panelRepository.findByIdWithDetails(panelId)
                .orElseThrow(() -> new RuntimeException("Panel not found"));
        panel.setPanelRequests(new HashSet<>(requestRepository.findByPanelIdWithDetails(panelId)));
        return panel;
    }

    private void mergeAdjacentSlots(AvailabilitySlot restoredSlot) {
        Long interviewerId = restoredSlot.getInterviewer().getId();
        LocalDateTime mergedStart = restoredSlot.getStartDateTime();
        LocalDateTime mergedEnd   = restoredSlot.getEndDateTime();
        boolean changed = false;

        List<AvailabilitySlot> beforeSlots = slotRepository.findActiveAvailableSlotsEndingAt(interviewerId, mergedStart);
        if (!beforeSlots.isEmpty()) {
            AvailabilitySlot before = beforeSlots.get(0);
            logger.info("Panel cancel: merging before-fragment slot {} → slot {}",
                    before.getId(), restoredSlot.getId());
            mergedStart = before.getStartDateTime();
            before.setActive(false);
            slotRepository.save(before);
            deactivateExtraSlots(beforeSlots, before);
            changed = true;
        }

        List<AvailabilitySlot> afterSlots = slotRepository.findActiveAvailableSlotsStartingAt(interviewerId, mergedEnd);
        if (!afterSlots.isEmpty()) {
            AvailabilitySlot after = afterSlots.get(0);
            logger.info("Panel cancel: merging after-fragment slot {} → slot {}",
                    after.getId(), restoredSlot.getId());
            mergedEnd = after.getEndDateTime();
            after.setActive(false);
            slotRepository.save(after);
            deactivateExtraSlots(afterSlots, after);
            changed = true;
        }

        if (changed) {
            restoredSlot.setStartDateTime(mergedStart);
            restoredSlot.setEndDateTime(mergedEnd);
            slotRepository.save(restoredSlot);
            logger.info("Slot {} merged → {} – {}", restoredSlot.getId(), mergedStart, mergedEnd);
        }
    }

    private void deactivateExtraSlots(List<AvailabilitySlot> slots, AvailabilitySlot keep) {
        for (AvailabilitySlot slot : slots) {
            if (!slot.getId().equals(keep.getId())) {
                slot.setActive(false);
                slotRepository.save(slot);
            }
        }
    }

    private AvailabilitySlot splitSlot(AvailabilitySlot slot,
                                       LocalDateTime bookStart,
                                       LocalDateTime bookEnd,
                                       String candidateName) {
        LocalDateTime slotStart = slot.getStartDateTime();
        LocalDateTime slotEnd = slot.getEndDateTime();
        boolean isFullBooking = bookStart.equals(slotStart) && bookEnd.equals(slotEnd);

        if (isFullBooking) {
            slot.setStatus(SlotStatus.BOOKED);
            slot.setDescription("Panel Interview: " + candidateName);
            return slotRepository.save(slot);
        }

        slot.setActive(false);
        slotRepository.save(slot);

        if (bookStart.isAfter(slotStart)) {
            slotRepository.save(AvailabilitySlot.builder()
                    .interviewer(slot.getInterviewer())
                    .startDateTime(slotStart).endDateTime(bookStart)
                    .description(slot.getDescription())
                    .status(SlotStatus.AVAILABLE).isActive(true).build());
        }

        AvailabilitySlot booked = AvailabilitySlot.builder()
                .interviewer(slot.getInterviewer())
                .startDateTime(bookStart).endDateTime(bookEnd)
                .description("Panel Interview: " + candidateName)
                .status(SlotStatus.BOOKED).isActive(true).build();
        booked = slotRepository.save(booked);

        if (bookEnd.isBefore(slotEnd)) {
            slotRepository.save(AvailabilitySlot.builder()
                    .interviewer(slot.getInterviewer())
                    .startDateTime(bookEnd).endDateTime(slotEnd)
                    .description(slot.getDescription())
                    .status(SlotStatus.AVAILABLE).isActive(true).build());
        }

        return booked;
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