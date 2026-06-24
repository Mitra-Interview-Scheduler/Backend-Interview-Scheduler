package com.nemal.dto;

import com.nemal.entity.AvailabilitySlot;
import com.nemal.enums.InterviewStatus;
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
        String candidateName,   // populated for BOOKED slots via description or schedule chain
        String interviewStatus  // SCHEDULED | COMPLETED | CANCELLED
) {
    public static AvailabilitySlotDto from(AvailabilitySlot slot) {
        return from(slot, null);
    }

    public static AvailabilitySlotDto from(AvailabilitySlot slot, InterviewStatus effectiveInterviewStatus) {
        String candidateName = null;
        String interviewStatus = null;

        // 1. Try via interviewSchedule → request chain (set for full bookings)
        if (slot.getStatus() == SlotStatus.BOOKED
                && slot.getInterviewSchedule() != null) {
            if (effectiveInterviewStatus != null) {
                interviewStatus = effectiveInterviewStatus.name();
            } else if (slot.getInterviewSchedule().getStatus() != null) {
                interviewStatus = slot.getInterviewSchedule().getStatus().name();
            }
            if (slot.getInterviewSchedule().getRequest() != null) {
                candidateName = slot.getInterviewSchedule().getRequest().getCandidateName();
            }
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
                candidateName,
                interviewStatus
        );
    }
}