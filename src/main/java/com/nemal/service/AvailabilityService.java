package com.nemal.service;

import com.nemal.dto.AvailabilitySlotDto;
import com.nemal.dto.BulkAvailabilitySlotDto;
import com.nemal.dto.CreateAvailabilitySlotDto;
import com.nemal.dto.InterviewPostponeRequestDto;
import com.nemal.dto.UpdateAvailabilitySlotDto;
import com.nemal.entity.AvailabilitySlot;
import com.nemal.entity.User;
import java.util.Locale;
import com.nemal.enums.SlotStatus;
import com.nemal.repository.AvailabilitySlotRepository;
import org.springframework.context.annotation.Lazy;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.LocalTime;
import java.time.DayOfWeek;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class AvailabilityService {

    /**
     * How far back (in days) the interviewer calendar shows past slots.
     * Booked/completed slots within this window remain visible so interviewers
     * can review their recent history without data appearing to vanish.
     */
    private static final int INTERVIEWER_LOOKBACK_DAYS = 14;
    private static final int SLOT_LOOKBACK_MINUTES = 15;

    private final AvailabilitySlotRepository availabilitySlotRepository;
    private final InterviewRequestService interviewRequestService;
    private final InterviewPostponeRequestService postponeRequestService;
    private final CalendarSyncService calendarSyncService;

    public AvailabilityService(
            AvailabilitySlotRepository availabilitySlotRepository,
            InterviewRequestService interviewRequestService,
            @Lazy InterviewPostponeRequestService postponeRequestService,
            CalendarSyncService calendarSyncService) {
        this.availabilitySlotRepository = availabilitySlotRepository;
        this.interviewRequestService = interviewRequestService;
        this.postponeRequestService = postponeRequestService;
        this.calendarSyncService = calendarSyncService;
    }

    // ── Read ──────────────────────────────────────────────────────────────────

    public List<AvailabilitySlotDto> getInterviewerAvailability(User interviewer) {
        LocalDateTime from = LocalDateTime.now().minusDays(INTERVIEWER_LOOKBACK_DAYS);
        return mapSlotsWithPostpone(availabilitySlotRepository
                .findByInterviewerIdAndIsActiveTrueWithLookback(interviewer.getId(), from));
    }

    public List<AvailabilitySlotDto> getInterviewerAvailabilityByDateRange(
            User interviewer, LocalDateTime start, LocalDateTime end) {
        return mapSlotsWithPostpone(availabilitySlotRepository
                .findByInterviewerIdAndStartDateTimeBetweenAndIsActiveTrue(
                        interviewer.getId(), start, end));
    }

    public List<AvailabilitySlotDto> getInterviewerAvailabilityByDateRange(
            User interviewer, LocalDateTime start, LocalDateTime end, Integer page, Integer size) {
        int safePage = page != null ? Math.max(0, page) : 0;
        int safeSize = size != null ? Math.max(1, size) : 200;
        return mapSlotsWithPostpone(availabilitySlotRepository
                .findByInterviewerIdAndStartDateTimeBetweenAndIsActiveTruePaged(
                        interviewer.getId(),
                        start,
                        end,
                        PageRequest.of(safePage, safeSize))
                .getContent());
    }

    private List<AvailabilitySlotDto> mapSlotsWithPostpone(List<AvailabilitySlot> slots) {
        List<Long> scheduleIds = slots.stream()
                .map(AvailabilitySlot::getInterviewSchedule)
                .filter(Objects::nonNull)
                .map(schedule -> schedule.getId())
                .filter(Objects::nonNull)
                .distinct()
                .toList();

        Map<Long, InterviewPostponeRequestDto> pendingBySchedule =
                postponeRequestService.findPendingByScheduleIds(scheduleIds);

        return slots.stream()
                .map(slot -> {
                    AvailabilitySlotDto dto = toAvailabilitySlotDto(slot);
                    Long scheduleId = dto.interviewScheduleId();
                    if (scheduleId != null && pendingBySchedule.containsKey(scheduleId)) {
                        InterviewPostponeRequestDto pending = pendingBySchedule.get(scheduleId);
                        dto = dto.withPendingPostpone(
                                pending.id(),
                                pending.reason(),
                                pending.createdAt(),
                                pending.preferredStartDateTime(),
                                pending.preferredEndDateTime(),
                                pending.requestedByName());
                    }
                    return dto;
                })
                .collect(Collectors.toList());
    }

    private AvailabilitySlotDto toAvailabilitySlotDto(AvailabilitySlot slot) {
        return AvailabilitySlotDto.from(
                slot,
                interviewRequestService.resolveEffectiveInterviewStatus(slot.getInterviewSchedule()));
    }

    // ── Validation ────────────────────────────────────────────────────────────

    /**
     * Validates that a slot's time window is acceptable:
     *  - Start may be up to {@value SLOT_LOOKBACK_MINUTES} minutes before now (UTC).
     *  - End must be after now and after start.
     */
    private void validateSlotTimes(LocalDateTime start, LocalDateTime end) {
        LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
        LocalDateTime minAllowedStart = now.minusMinutes(SLOT_LOOKBACK_MINUTES);
        if (start.isBefore(minAllowedStart)) {
            throw new RuntimeException(
                    "Start time cannot be more than " + SLOT_LOOKBACK_MINUTES + " minutes before now");
        }
        if (!end.isAfter(now)) {
            throw new RuntimeException("End time must be after the current time");
        }
        if (!end.isAfter(start)) {
            throw new RuntimeException("End time must be after start time");
        }
    }

    // ── Write ─────────────────────────────────────────────────────────────────

    @Transactional
    public AvailabilitySlotDto createAvailabilitySlot(User interviewer, CreateAvailabilitySlotDto dto) {
        calendarSyncService.ensureInterviewerConnected(interviewer);
        validateSlotTimes(dto.startDateTime(), dto.endDateTime());

        List<AvailabilitySlot> conflicts = availabilitySlotRepository.findConflictingSlots(
                interviewer.getId(), dto.startDateTime(), dto.endDateTime());

        if (!conflicts.isEmpty()) {
            throw new RuntimeException("This time slot conflicts with existing availability");
        }

        AvailabilitySlot slot = AvailabilitySlot.builder()
                .interviewer(interviewer)
                .startDateTime(dto.startDateTime())
                .endDateTime(dto.endDateTime())
                .description(dto.description())
            .recurrenceGroupId(resolveRecurrenceGroupId(dto.recurrenceGroupId()))
                .status(SlotStatus.AVAILABLE)
                .isActive(true)
                .build();

        slot = availabilitySlotRepository.save(slot);
        calendarSyncService.syncAvailabilitySlotCreated(slot);
        return AvailabilitySlotDto.from(slot);
    }

    @Transactional
        public AvailabilitySlotDto updateAvailabilitySlot(
            User interviewer, Long slotId, UpdateAvailabilitySlotDto dto, String scope) {

        AvailabilitySlot slot = availabilitySlotRepository.findById(slotId)
                .orElseThrow(() -> new RuntimeException("Slot not found: " + slotId));

        if (!slot.getInterviewer().getId().equals(interviewer.getId())) {
            throw new RuntimeException("Unauthorized: this slot does not belong to you");
        }
        calendarSyncService.ensureInterviewerConnected(interviewer);
        if (slot.getStatus() == SlotStatus.BOOKED) {
            throw new RuntimeException("Cannot edit a booked slot — it has an interview scheduled");
        }
        if (!slot.isActive()) {
            throw new RuntimeException("Cannot edit an inactive slot");
        }

        validateSlotTimes(dto.startDateTime(), dto.endDateTime());

        DeleteScope updateScope = DeleteScope.from(scope);
        boolean isRecurringEdit = updateScope != DeleteScope.SINGLE && slot.getRecurrenceGroupId() != null && !slot.getRecurrenceGroupId().isBlank();
        // capture the recurrence group id in a final local variable so it can be referenced from lambdas
        final String slotRecurrenceGroupId = slot.getRecurrenceGroupId();

        // Conflict check — exclude the slot being edited and all slots in its recurrence group (for recurring edits)
        List<AvailabilitySlot> conflicts = availabilitySlotRepository
                .findConflictingSlots(interviewer.getId(), dto.startDateTime(), dto.endDateTime())
                .stream()
                .filter(s -> !s.getId().equals(slotId))
                .filter(s -> !isRecurringEdit || !slotRecurrenceGroupId.equals(s.getRecurrenceGroupId()))
                .collect(Collectors.toList());

        if (!conflicts.isEmpty()) {
            throw new RuntimeException("The updated time conflicts with another existing availability slot");
        }

        if (!isRecurringEdit) {
            applyUpdateToSlot(slot, dto.startDateTime(), dto.endDateTime(), dto.description());
            slot = availabilitySlotRepository.save(slot);
            calendarSyncService.syncAvailabilitySlotUpdated(slot);
            return AvailabilitySlotDto.from(slot);
        }

        List<AvailabilitySlot> seriesSlots = updateScope == DeleteScope.FUTURE
                ? availabilitySlotRepository.findByInterviewerIdAndRecurrenceGroupIdAndStartDateTimeGreaterThanEqualAndIsActiveTrue(
                        interviewer.getId(), slot.getRecurrenceGroupId(), slot.getStartDateTime())
                : availabilitySlotRepository.findByInterviewerIdAndRecurrenceGroupIdAndIsActiveTrue(
                        interviewer.getId(), slot.getRecurrenceGroupId());

        DayOfWeek selectedDayOfWeek = slot.getStartDateTime().getDayOfWeek();
        boolean isAllScope = updateScope == DeleteScope.ALL;

        List<AvailabilitySlot> editableSlots = seriesSlots.stream()
            .filter(s -> !isAllScope || s.getStartDateTime().getDayOfWeek() == selectedDayOfWeek)
                .filter(s -> s.getStatus() != SlotStatus.BOOKED)
                .collect(Collectors.toList());

        if (editableSlots.isEmpty()) {
            throw new RuntimeException("No editable recurring slots found for selected scope");
        }

        AvailabilitySlot updatedAnchor = null;
        for (AvailabilitySlot recurringSlot : editableSlots) {
            applyUpdateToSlot(recurringSlot, dto.startDateTime(), dto.endDateTime(), dto.description());
            updatedAnchor = recurringSlot;
        }

        availabilitySlotRepository.saveAll(editableSlots);
        editableSlots.forEach(calendarSyncService::syncAvailabilitySlotUpdated);
        return AvailabilitySlotDto.from(updatedAnchor != null ? updatedAnchor : slot);
    }

    @Transactional
    public List<AvailabilitySlotDto> createBulkAvailabilitySlots(
            User interviewer, BulkAvailabilitySlotDto bulkDto) {
        if (bulkDto.slots() == null || bulkDto.slots().isEmpty()) {
            throw new RuntimeException("At least one slot is required");
        }

        String sharedGroupId = resolveBulkGroupId(bulkDto);

        return bulkDto.slots().stream()
                .map(dto -> createAvailabilitySlot(interviewer, new CreateAvailabilitySlotDto(
                        dto.startDateTime(),
                        dto.endDateTime(),
                        dto.currentTime(),
                        dto.description(),
                        sharedGroupId
                )))
                .collect(Collectors.toList());
    }

    @Transactional
        public void deleteAvailabilitySlot(User interviewer, Long slotId, String scope) {
        AvailabilitySlot slot = availabilitySlotRepository.findById(slotId)
                .orElseThrow(() -> new RuntimeException("Slot not found"));

        if (!slot.getInterviewer().getId().equals(interviewer.getId())) {
            throw new RuntimeException("Unauthorized");
        }
        calendarSyncService.ensureInterviewerConnected(interviewer);
        if (slot.getStatus() == SlotStatus.BOOKED) {
            throw new RuntimeException("Cannot delete booked slots");
        }

        DeleteScope deleteScope = DeleteScope.from(scope);
        String recurrenceGroupId = slot.getRecurrenceGroupId();

        if (deleteScope == DeleteScope.SINGLE || recurrenceGroupId == null || recurrenceGroupId.isBlank()) {
            hardDeleteSlot(slot);
            return;
        }

        List<AvailabilitySlot> seriesSlots = deleteScope == DeleteScope.FUTURE
                ? availabilitySlotRepository
                .findByInterviewerIdAndRecurrenceGroupIdAndStartDateTimeGreaterThanEqualAndIsActiveTrue(
                        interviewer.getId(), recurrenceGroupId, slot.getStartDateTime())
                : availabilitySlotRepository
                .findByInterviewerIdAndRecurrenceGroupIdAndIsActiveTrue(interviewer.getId(), recurrenceGroupId);

        DayOfWeek selectedDayOfWeek = slot.getStartDateTime().getDayOfWeek();
        boolean isAllScope = deleteScope == DeleteScope.ALL;

        List<AvailabilitySlot> deletableSlots = seriesSlots.stream()
            .filter(s -> !isAllScope || s.getStartDateTime().getDayOfWeek() == selectedDayOfWeek)
                .filter(s -> s.getStatus() != SlotStatus.BOOKED)
                .collect(Collectors.toList());

        if (deletableSlots.isEmpty()) {
            throw new RuntimeException("No deletable recurring slots found for selected scope");
        }

        deletableSlots.forEach(this::hardDeleteSlot);
    }

    private void hardDeleteSlot(AvailabilitySlot slot) {
        calendarSyncService.syncAvailabilitySlotDeleted(slot);
        availabilitySlotRepository.delete(slot);
    }

    private String resolveRecurrenceGroupId(String recurrenceGroupId) {
        if (recurrenceGroupId == null || recurrenceGroupId.isBlank()) {
            return null;
        }
        return recurrenceGroupId;
    }

    private String resolveBulkGroupId(BulkAvailabilitySlotDto bulkDto) {
        String existingGroupId = bulkDto.slots().stream()
                .map(CreateAvailabilitySlotDto::recurrenceGroupId)
                .filter(value -> value != null && !value.isBlank())
                .findFirst()
                .orElse(null);

        if (existingGroupId != null) {
            return existingGroupId;
        }

        return bulkDto.slots().size() > 1 ? UUID.randomUUID().toString() : null;
    }

    private void applyUpdateToSlot(AvailabilitySlot slot, LocalDateTime templateStart, LocalDateTime templateEnd, String description) {
        LocalDate slotDate = slot.getStartDateTime().toLocalDate();
        LocalTime startTime = templateStart.toLocalTime();
        LocalTime endTime = templateEnd.toLocalTime();

        slot.setStartDateTime(LocalDateTime.of(slotDate, startTime));
        slot.setEndDateTime(LocalDateTime.of(slotDate, endTime));
        if (description != null) {
            slot.setDescription(description);
        }
    }

    private enum DeleteScope {
        SINGLE,
        ALL,
        FUTURE;

        private static DeleteScope from(String rawScope) {
            if (rawScope == null || rawScope.isBlank()) {
                return SINGLE;
            }

            try {
                return DeleteScope.valueOf(rawScope.trim().toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException ex) {
                throw new RuntimeException("Invalid delete scope: " + rawScope);
            }
        }
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
