package com.nemal.service;

import com.nemal.dto.AvailabilitySlotDto;
import com.nemal.dto.BulkAvailabilitySlotDto;
import com.nemal.dto.CreateAvailabilitySlotDto;
import com.nemal.dto.UpdateAvailabilitySlotDto;
import com.nemal.entity.AvailabilitySlot;
import com.nemal.entity.User;
import com.nemal.enums.SlotStatus;
import com.nemal.repository.AvailabilitySlotRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class AvailabilityService {

    /**
     * How far back (in days) the interviewer calendar shows past slots.
     * Booked/completed slots within this window remain visible so interviewers
     * can review their recent history without data appearing to vanish.
     */
    private static final int INTERVIEWER_LOOKBACK_DAYS = 14;

    /**
     * Minimum hours of lead time required for same-day availability slots.
     * Prevents interviewers from accidentally accepting last-minute sessions
     * without enough prep time.
     */
    private static final double SAME_DAY_MIN_LEAD_HOURS = 0.25;

    private final AvailabilitySlotRepository availabilitySlotRepository;

    public AvailabilityService(AvailabilitySlotRepository availabilitySlotRepository) {
        this.availabilitySlotRepository = availabilitySlotRepository;
    }

    // ── Read ──────────────────────────────────────────────────────────────────

    public List<AvailabilitySlotDto> getInterviewerAvailability(User interviewer) {
        LocalDateTime from = LocalDateTime.now().minusDays(INTERVIEWER_LOOKBACK_DAYS);
        return availabilitySlotRepository
                .findByInterviewerIdAndIsActiveTrueWithLookback(interviewer.getId(), from)
                .stream()
                .map(AvailabilitySlotDto::from)
                .collect(Collectors.toList());
    }

    public List<AvailabilitySlotDto> getInterviewerAvailabilityByDateRange(
            User interviewer, LocalDateTime start, LocalDateTime end) {
        return availabilitySlotRepository
                .findByInterviewerIdAndStartDateTimeBetweenAndIsActiveTrue(
                        interviewer.getId(), start, end)
                .stream()
                .map(AvailabilitySlotDto::from)
                .collect(Collectors.toList());
    }

    // ── Validation ────────────────────────────────────────────────────────────

    /**
     * Validates that a slot's time window is acceptable:
     *  - Past days are rejected entirely.
     *  - Same-day slots must start at least {@value SAME_DAY_MIN_LEAD_HOURS} hours from now.
     *  - Future days are always allowed.
     *  - End must be after start.
     */
    private void validateSlotTimes(LocalDateTime start, LocalDateTime end,LocalDateTime now) {
        LocalDate currentTime = now.toLocalDate();
        // Reject past dates
        if (start.toLocalDate().isBefore(currentTime)) {
            throw new RuntimeException("Cannot create availability slots for past dates");
        }
        // Same-day: require at least SAME_DAY_MIN_LEAD_HOURS hours of lead time
        if (start.toLocalDate().equals(currentTime)) {
            LocalDateTime minAllowed = now.plusHours((long) SAME_DAY_MIN_LEAD_HOURS);
            if (start.isBefore(minAllowed)) {
                String earliest = minAllowed.format(DateTimeFormatter.ofPattern("HH:mm"));
                throw new RuntimeException(
                        "Same-day slots must start at least " + SAME_DAY_MIN_LEAD_HOURS +
                                " hours from now. Earliest allowed start today: " + earliest);
            }
        }

        if (!end.isAfter(start)) {
            throw new RuntimeException("End time must be after start time");
        }
    }

    // ── Write ─────────────────────────────────────────────────────────────────

    @Transactional
    public AvailabilitySlotDto createAvailabilitySlot(User interviewer, CreateAvailabilitySlotDto dto) {
        validateSlotTimes(dto.startDateTime(), dto.endDateTime(),dto.currentTime());

        System.out.println( "Validating slot times: start=" + dto.startDateTime() + ", end=" + dto.endDateTime() + ", now=" + dto.currentTime() );
        List<AvailabilitySlot> conflicts = availabilitySlotRepository.findConflictingSlots(
                interviewer.getId(), dto.startDateTime(), dto.endDateTime());

        if (!conflicts.isEmpty()) {
            System.out.println( "Conflict detected with existing slots: " + conflicts.stream().map(s -> s.getId()).collect(Collectors.toList()) );
            throw new RuntimeException("This time slot conflicts with existing availability");
        }
        System.out.println( "No conflicts detected, creating slot for interviewer " + conflicts.stream().map(s -> s.getId()).collect(Collectors.toList()));

        AvailabilitySlot slot = AvailabilitySlot.builder()
                .interviewer(interviewer)
                .startDateTime(dto.startDateTime())
                .endDateTime(dto.endDateTime())
                .description(dto.description())
                .status(SlotStatus.AVAILABLE)
                .isActive(true)
                .build();

        slot = availabilitySlotRepository.save(slot);
        return AvailabilitySlotDto.from(slot);
    }

    @Transactional
    public AvailabilitySlotDto updateAvailabilitySlot(
            User interviewer, Long slotId, UpdateAvailabilitySlotDto dto) {

        AvailabilitySlot slot = availabilitySlotRepository.findById(slotId)
                .orElseThrow(() -> new RuntimeException("Slot not found: " + slotId));

        if (!slot.getInterviewer().getId().equals(interviewer.getId())) {
            throw new RuntimeException("Unauthorized: this slot does not belong to you");
        }
        if (slot.getStatus() == SlotStatus.BOOKED) {
            throw new RuntimeException("Cannot edit a booked slot — it has an interview scheduled");
        }
        if (!slot.isActive()) {
            throw new RuntimeException("Cannot edit an inactive slot");
        }

        validateSlotTimes(dto.startDateTime(), dto.endDateTime(),dto.currentTime());

        // Conflict check — exclude the slot being edited
        List<AvailabilitySlot> conflicts = availabilitySlotRepository
                .findConflictingSlots(interviewer.getId(), dto.startDateTime(), dto.endDateTime())
                .stream()
                .filter(s -> !s.getId().equals(slotId))
                .collect(Collectors.toList());

        if (!conflicts.isEmpty()) {
            throw new RuntimeException("The updated time conflicts with another existing availability slot");
        }

        slot.setStartDateTime(dto.startDateTime());
        slot.setEndDateTime(dto.endDateTime());
        if (dto.description() != null) {
            slot.setDescription(dto.description());
        }

        slot = availabilitySlotRepository.save(slot);
        return AvailabilitySlotDto.from(slot);
    }

    @Transactional
    public List<AvailabilitySlotDto> createBulkAvailabilitySlots(
            User interviewer, BulkAvailabilitySlotDto bulkDto) {
        return bulkDto.slots().stream()
                .map(dto -> createAvailabilitySlot(interviewer, dto))
                .collect(Collectors.toList());
    }

    @Transactional
    public void deleteAvailabilitySlot(User interviewer, Long slotId) {
        AvailabilitySlot slot = availabilitySlotRepository.findById(slotId)
                .orElseThrow(() -> new RuntimeException("Slot not found"));

        if (!slot.getInterviewer().getId().equals(interviewer.getId())) {
            throw new RuntimeException("Unauthorized");
        }
        if (slot.getStatus() == SlotStatus.BOOKED) {
            throw new RuntimeException("Cannot delete booked slots");
        }

        slot.setActive(false);
        availabilitySlotRepository.save(slot);
    }

    // ── Stats ─────────────────────────────────────────────────────────────────

    public long getAvailableSlotCount(Long interviewerId ,LocalDateTime now) {
        return availabilitySlotRepository.countAvailableSlotsFrom(
                interviewerId, now);
    }

    public long getBookedSlotCount(Long interviewerId, LocalDateTime now) {
        return availabilitySlotRepository.countBookedSlotsFrom(
                interviewerId, now);
    }

    public long getRecentBookedSlotCount(Long interviewerId, int lookbackDays) {
        LocalDateTime from = LocalDateTime.now().minusDays(lookbackDays);
        return availabilitySlotRepository.countBookedSlotsFrom(interviewerId, from);
    }
}