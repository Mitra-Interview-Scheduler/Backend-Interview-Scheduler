package com.nemal.service;

import com.nemal.dto.CreateInterviewRequestDto;
import com.nemal.dto.InterviewRequestDto;
import com.nemal.entity.*;
import com.nemal.enums.CandidateStatus;
import com.nemal.enums.InterviewStatus;
import com.nemal.enums.RequestStatus;
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

        InterviewRequest request = InterviewRequest.builder()
                .candidateName(candidateName)
                .candidate(candidate)
                .candidateDesignation(candidateDesignation)
                .preferredStartDateTime(bookingStart)
                .preferredEndDateTime(bookingEnd)
                .requestedBy(requestedBy)
                .assignedInterviewer(slot.getInterviewer())
                .availabilitySlot(bookedSlot)
                .status(RequestStatus.ACCEPTED)
                .respondedAt(LocalDateTime.now())
                .responseNotes("Auto-accepted by HR scheduling")
                .isUrgent(dto.isUrgent())
                .notes(dto.notes())
                .build();

        request.getRequiredTechnologies().addAll(technologies);
        InterviewRequest saved = interviewRequestRepository.save(request);

        InterviewSchedule schedule = InterviewSchedule.builder()
                .request(saved)
                .interviewer(slot.getInterviewer())
                .startDateTime(bookingStart)
                .endDateTime(bookingEnd)
                .status(InterviewStatus.SCHEDULED)
                .build();
        schedule = interviewScheduleRepository.save(schedule);

        bookedSlot.setInterviewSchedule(schedule);
        availabilitySlotRepository.save(bookedSlot);

        if (candidate != null) {
            candidate.setStatus(CandidateStatus.SCHEDULED);
            candidateRepository.save(candidate);
        }

        try {
            notificationService.sendInterviewScheduledNotification(saved);
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
        return interviewRequestRepository.findByInterviewScheduleId(interviewScheduleId);
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
        return interviewRequestRepository.findByCandidateId(candidateId)
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
                candidate.setStatus(CandidateStatus.SCREENING);
                candidateRepository.save(candidate);
                logger.info("Candidate {} reset to SCREENING", candidate.getId());
            }
        }
    }

    // ── Private helpers ───────────────────────────────────────────────────────

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
}