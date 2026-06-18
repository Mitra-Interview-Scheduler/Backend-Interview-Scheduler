package com.nemal.service;

import com.nemal.dto.CreatePanelInterviewDto;
import com.nemal.dto.InterviewPanelDto;
import com.nemal.entity.*;
import com.nemal.enums.InterviewStatus;
import com.nemal.enums.InterviewType;
import com.nemal.enums.MasterStatus;
import com.nemal.enums.RequestStatus;
import com.nemal.enums.SlotStatus;
import com.nemal.repository.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
            MasterStepService masterStepService) {
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

        InterviewPanel panel = InterviewPanel.builder()
                .candidate(candidate)
                .candidateName(candidateName)
                .startDateTime(dto.startDateTime())
                .endDateTime(dto.endDateTime())
                .requestedBy(requestedBy)
                .isUrgent(dto.isUrgent())
                .notes(dto.notes())
                .build();
        panel = panelRepository.save(panel);

        Designation finalDesignation = designation;
        String finalCandidateName = candidateName;
        Set<Technology> finalTechnologies = technologies;
        InterviewType interviewType = InterviewType.fromValue(dto.interviewType());

        for (AvailabilitySlot slot : slots) {
            AvailabilitySlot bookedSlot = splitSlot(slot, dto.startDateTime(), dto.endDateTime(), finalCandidateName);

            InterviewRequest request = InterviewRequest.builder()
                    .candidateName(finalCandidateName)
                    .candidate(candidate)
                    .candidateDesignation(finalDesignation)
                    .requiredTechnologies(new HashSet<>(finalTechnologies))
                    .preferredStartDateTime(dto.startDateTime())
                    .preferredEndDateTime(dto.endDateTime())
                    .requestedBy(requestedBy)
                    .assignedInterviewer(slot.getInterviewer())
                    .availabilitySlot(bookedSlot)
                    .panel(panel)
                    .status(RequestStatus.ACCEPTED)
                    .respondedAt(LocalDateTime.now())
                    .isUrgent(dto.isUrgent())
                    .notes(dto.notes())
                    .responseNotes("Auto-accepted as part of panel interview")
                    .build();

            request = requestRepository.save(request);

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

            try {
                notificationService.sendInterviewScheduledNotification(request);
            } catch (Exception e) {
                logger.warn("Failed to send scheduled notification to {}: {}", slot.getInterviewer().getFullName(), e.getMessage());
            }
        }

        if (candidate != null) {
            MasterStatus targetStatus = interviewType.toCandidateStatus();
            MasterStatus oldStatus = candidate.getStatus();
            masterStepService.assignStatus(candidate, targetStatus);
            candidateRepository.save(candidate);
            candidateStepPipelineService.updatePipelineOnStatusChange(
                    candidate.getId(), targetStatus, oldStatus, true);
        }

        InterviewPanel savedPanel = panelRepository.findByIdWithDetails(panel.getId())
                .orElseThrow(() -> new RuntimeException("Panel not found after save"));
        return InterviewPanelDto.from(savedPanel);
    }

    @Transactional
    public void cancelPanelInterview(User hrUser, Long panelId) {
        InterviewPanel panel = panelRepository.findByIdWithDetails(panelId)
                .orElseThrow(() -> new RuntimeException("Panel not found"));

        if (!panel.getRequestedBy().getId().equals(hrUser.getId())) {
            throw new RuntimeException("Unauthorized — you did not create this panel");
        }

        for (InterviewRequest request : panel.getPanelRequests()) {
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
            }

            scheduleRepository.findByRequestId(request.getId()).ifPresent(schedule -> {
                schedule.setStatus(InterviewStatus.CANCELLED);
                scheduleRepository.save(schedule);
            });

            request.setStatus(RequestStatus.CANCELLED);
            request.setAvailabilitySlot(null);
            requestRepository.save(request);

            try {
                notificationService.sendInterviewCancelledNotification(request);
            } catch (Exception e) {
                logger.warn("Failed to send cancellation notification: {}", e.getMessage());
            }
        }

        if (panel.getCandidate() != null) {
            Candidate candidate = panel.getCandidate();
            long activeCount = requestRepository.findByCandidateId(candidate.getId())
                    .stream()
                    .filter(r -> r.getStatus() == RequestStatus.ACCEPTED)
                    .count();
            if (activeCount == 0) {
                masterStepService.assignStatus(candidate, MasterStatus.SCREENING);
                candidateRepository.save(candidate);
            }
        }

        logger.info("Cancelled panel {} — all slots restored and merged", panelId);
    }

    // ── Read ──────────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<InterviewPanelDto> getPanelsByCandidateId(Long candidateId) {
        String candidateName = candidateRepository.findById(candidateId)
                .map(Candidate::getName)
                .orElse(null);
        return panelRepository.findByCandidateIdOrName(candidateId, candidateName)
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

    private void mergeAdjacentSlots(AvailabilitySlot restoredSlot) {
        Long interviewerId = restoredSlot.getInterviewer().getId();
        LocalDateTime mergedStart = restoredSlot.getStartDateTime();
        LocalDateTime mergedEnd   = restoredSlot.getEndDateTime();
        boolean changed = false;

        var before = slotRepository.findActiveAvailableSlotEndingAt(interviewerId, mergedStart);
        if (before.isPresent()) {
            logger.info("Panel cancel: merging before-fragment slot {} → slot {}",
                    before.get().getId(), restoredSlot.getId());
            mergedStart = before.get().getStartDateTime();
            before.get().setActive(false);
            slotRepository.save(before.get());
            changed = true;
        }

        var after = slotRepository.findActiveAvailableSlotStartingAt(interviewerId, mergedEnd);
        if (after.isPresent()) {
            logger.info("Panel cancel: merging after-fragment slot {} → slot {}",
                    after.get().getId(), restoredSlot.getId());
            mergedEnd = after.get().getEndDateTime();
            after.get().setActive(false);
            slotRepository.save(after.get());
            changed = true;
        }

        if (changed) {
            restoredSlot.setStartDateTime(mergedStart);
            restoredSlot.setEndDateTime(mergedEnd);
            slotRepository.save(restoredSlot);
            logger.info("Slot {} merged → {} – {}", restoredSlot.getId(), mergedStart, mergedEnd);
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
}