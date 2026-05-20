package com.nemal.dto;

import com.nemal.entity.AvailabilitySlot;
import com.nemal.enums.SlotStatus;

import java.time.LocalDateTime;

public record AvailabilitySlotDto(
        Long id,
        LocalDateTime startDateTime,
        LocalDateTime endDateTime,
        String status,
        String description,
    String recurrenceGroupId,
    boolean isRecurring,
        Long interviewScheduleId,
        Double durationHours,   // new field for duration in hours
        String candidateName   // populated for BOOKED slots via description or schedule chain
) {
    public static AvailabilitySlotDto from(AvailabilitySlot slot) {
        String candidateName = null;

        // 1. Try via interviewSchedule → request chain (set for full bookings)
        if (slot.getStatus() == SlotStatus.BOOKED
                && slot.getInterviewSchedule() != null
                && slot.getInterviewSchedule().getRequest() != null) {
            candidateName = slot.getInterviewSchedule().getRequest().getCandidateName();
        }

        // 2. Fall back to description pattern "Interview: John" or "Panel Interview: John"
        if (candidateName == null && slot.getDescription() != null) {
            String desc = slot.getDescription();
            if (desc.startsWith("Panel Interview: ")) {
                candidateName = desc.substring("Panel Interview: ".length()).trim();
            } else if (desc.startsWith("Interview: ")) {
                candidateName = desc.substring("Interview: ".length()).trim();
            }
        }

        return new AvailabilitySlotDto(
                slot.getId(),
                slot.getStartDateTime(),
                slot.getEndDateTime(),
                slot.getStatus().name(),
                slot.getDescription(),
                slot.getRecurrenceGroupId(),
                slot.getRecurrenceGroupId() != null,
                slot.getInterviewSchedule() != null ? slot.getInterviewSchedule().getId() : null,
                slot.getDurationHours(),
                candidateName
        );
    }
}