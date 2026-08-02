package com.nemal.dto;

import com.nemal.entity.AvailabilitySlot;
import com.nemal.entity.InterviewRequest;
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
        Double durationHours,
        String candidateName,
        String interviewStatus,
        String googleCalendarEventId,
        boolean googleCalendarSynced,
        String meetingLink,
        Long panelId,
        boolean hasPendingPostponeRequest,
        Long pendingPostponeRequestId,
        String pendingPostponeReason,
        LocalDateTime pendingPostponeRequestedAt,
        LocalDateTime pendingPostponePreferredStart,
        LocalDateTime pendingPostponePreferredEnd,
        String pendingPostponeRequestedByName
) {
    public static AvailabilitySlotDto from(AvailabilitySlot slot) {
        return from(slot, null);
    }

    public static AvailabilitySlotDto from(AvailabilitySlot slot, InterviewStatus effectiveInterviewStatus) {
        String candidateName = null;
        String interviewStatus = null;
        String meetingLink = null;
        Long panelId = null;

        if (slot.getStatus() == SlotStatus.BOOKED
                && slot.getInterviewSchedule() != null) {
            if (effectiveInterviewStatus != null) {
                interviewStatus = effectiveInterviewStatus.name();
            } else if (slot.getInterviewSchedule().getStatus() != null) {
                interviewStatus = slot.getInterviewSchedule().getStatus().name();
            }
            if (slot.getInterviewSchedule().getStatus() == InterviewStatus.SCHEDULED) {
                meetingLink = resolveMeetingLink(slot);
            }
            InterviewRequest request = slot.getInterviewSchedule().getRequest();
            if (request != null) {
                candidateName = request.getCandidateName();
                if (request.getPanel() != null) {
                    panelId = request.getPanel().getId();
                }
            }
        }

        if (candidateName == null && slot.getDescription() != null) {
            String desc = slot.getDescription();
            if (desc.startsWith("Panel Interview: ")) {
                candidateName = desc.substring("Panel Interview: ".length()).trim();
            } else if (desc.startsWith("Interview: ")) {
                candidateName = desc.substring("Interview: ".length()).trim();
            }
        }

        Double duration = slot.getDurationHours();
        if ((duration == null || duration <= 0)
                && slot.getStartDateTime() != null
                && slot.getEndDateTime() != null) {
            long seconds = java.time.Duration.between(slot.getStartDateTime(), slot.getEndDateTime()).getSeconds();
            duration = seconds > 0 ? seconds / 3600.0 : 0.0;
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
                duration,
                candidateName,
                interviewStatus,
                slot.getGoogleCalendarEventId(),
                slot.getGoogleCalendarEventId() != null,
                meetingLink,
                panelId,
                false,
                null,
                null,
                null,
                null,
                null,
                null
        );
    }

    public AvailabilitySlotDto withPendingPostpone(
            Long postponeRequestId,
            String reason,
            LocalDateTime requestedAt,
            LocalDateTime preferredStart,
            LocalDateTime preferredEnd,
            String requestedByName) {
        return new AvailabilitySlotDto(
                id,
                startDateTime,
                endDateTime,
                status,
                description,
                recurrenceGroupId,
                isRecurring,
                interviewScheduleId,
                durationHours,
                candidateName,
                interviewStatus,
                googleCalendarEventId,
                googleCalendarSynced,
                meetingLink,
                panelId,
                postponeRequestId != null,
                postponeRequestId,
                truncateReason(reason),
                requestedAt,
                preferredStart,
                preferredEnd,
                requestedByName
        );
    }

    private static String truncateReason(String reason) {
        if (reason == null || reason.isBlank()) {
            return null;
        }
        String trimmed = reason.trim();
        return trimmed.length() <= 240 ? trimmed : trimmed.substring(0, 237) + "...";
    }

    private static String resolveMeetingLink(AvailabilitySlot slot) {
        if (slot.getInterviewSchedule() == null
                || slot.getInterviewSchedule().getStatus() != InterviewStatus.SCHEDULED) {
            return null;
        }
        String link = slot.getInterviewSchedule().getMeetingLink();
        if (link != null && !link.isBlank()) {
            return link;
        }
        InterviewRequest request = slot.getInterviewSchedule().getRequest();
        if (request != null && request.getPanel() != null) {
            String panelLink = request.getPanel().getMeetingLink();
            if (panelLink != null && !panelLink.isBlank()) {
                return panelLink;
            }
        }
        return null;
    }
}
