package com.nemal.util;

import com.nemal.dto.*;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.List;

public final class TimeZoneMapper {
    private static final ZoneId UTC = ZoneOffset.UTC;

    private TimeZoneMapper() {}

    public static ZoneId resolveZone(String timezone) {
        try {
            return (timezone == null || timezone.isBlank()) ? UTC : ZoneId.of(timezone);
        } catch (Exception ignored) {
            return UTC;
        }
    }

    public static LocalDateTime toUtc(LocalDateTime local, ZoneId sourceZone) {
        if (local == null) return null;
        ZonedDateTime zoned = local.atZone(sourceZone);
        return zoned.withZoneSameInstant(UTC).toLocalDateTime();
    }

    public static LocalDateTime fromUtc(LocalDateTime utcTime, ZoneId targetZone) {
        if (utcTime == null) return null;
        ZonedDateTime zonedUtc = utcTime.atZone(UTC);
        return zonedUtc.withZoneSameInstant(targetZone).toLocalDateTime();
    }

    public static AvailabilitySlotDto fromUtc(AvailabilitySlotDto dto, ZoneId targetZone) {
        return new AvailabilitySlotDto(
                dto.id(),
                fromUtc(dto.startDateTime(), targetZone),
                fromUtc(dto.endDateTime(), targetZone),
                dto.status(),
                dto.description(),
                dto.recurrenceGroupId(),
                dto.isRecurring(),
                dto.interviewScheduleId(),
                dto.durationHours(),
                dto.candidateName(),
                dto.interviewStatus(),
                dto.googleCalendarEventId(),
                dto.googleCalendarSynced(),
                dto.meetingLink()
        );
    }

    public static List<AvailabilitySlotDto> fromUtcAvailability(List<AvailabilitySlotDto> slots, ZoneId targetZone) {
        return slots.stream().map(slot -> fromUtc(slot, targetZone)).toList();
    }

    public static InterviewerAvailabilityDto fromUtc(InterviewerAvailabilityDto dto, ZoneId targetZone) {
        return new InterviewerAvailabilityDto(
                dto.slotId(),
                dto.interviewerId(),
                dto.interviewerName(),
                dto.department(),
                dto.designation(),
                dto.yearsOfExperience(),
                dto.technologies(),
                dto.coreTechnologies(),
                dto.domains(),
                fromUtc(dto.startDateTime(), targetZone),
                fromUtc(dto.endDateTime(), targetZone),
                dto.status(),
                dto.candidateName(),
                dto.requestId(),
                dto.panelId(),
                dto.interviewerTierOrder(),
                dto.interviewerLevelOrder(),
                dto.interviewType(),
                dto.interviewScheduleId(),
                dto.interviewStatus(),
                dto.interviewCoordinatorName(),
                dto.coordinatedHrName(),
                dto.meetingLink()
        );
    }

    public static List<InterviewerAvailabilityDto> fromUtcInterviewerAvailability(List<InterviewerAvailabilityDto> slots, ZoneId targetZone) {
        return slots.stream().map(slot -> fromUtc(slot, targetZone)).toList();
    }

    public static InterviewRequestDto fromUtc(InterviewRequestDto dto, ZoneId targetZone) {
        return new InterviewRequestDto(
                dto.id(),
                dto.candidateName(),
                dto.candidateId(),
                dto.candidateDesignationId(),
                dto.candidateDesignationName(),
                dto.requiredTechnologies(),
                fromUtc(dto.preferredStartDateTime(), targetZone),
                fromUtc(dto.preferredEndDateTime(), targetZone),
                dto.requestedById(),
                dto.requestedByName(),
                dto.assignedInterviewerId(),
                dto.assignedInterviewerName(),
                dto.assignedInterviewerDesignationName(),
                dto.interviewCoordinatorId(),
                dto.interviewCoordinatorName(),
                dto.coordinatedHrName(),
                dto.availabilitySlotId(),
                dto.panelId(),
                dto.status(),
                fromUtc(dto.respondedAt(), targetZone),
                dto.responseNotes(),
                dto.isUrgent(),
                dto.notes(),
                fromUtc(dto.createdAt(), targetZone),
                dto.interviewScheduleId(),
                dto.interviewStatus(),
                dto.interviewType(),
                fromUtc(dto.scheduledStartDateTime(), targetZone),
                fromUtc(dto.scheduledEndDateTime(), targetZone),
                fromUtc(dto.interviewCompletedAt(), targetZone),
                dto.meetingLink(),
                dto.googleCalendarEventId()
        );
    }

    public static List<InterviewRequestDto> fromUtcInterviewRequests(List<InterviewRequestDto> requests, ZoneId targetZone) {
        return requests.stream().map(request -> fromUtc(request, targetZone)).toList();
    }

    public static CreateInterviewRequestDto toUtc(CreateInterviewRequestDto dto, ZoneId sourceZone) {
        return new CreateInterviewRequestDto(
                dto.candidateId(),
                dto.candidateName(),
                dto.candidateEmail(),
                dto.candidateDesignationId(),
                dto.requiredTechnologyIds(),
                dto.availabilitySlotId(),
                toUtc(dto.preferredStartDateTime(), sourceZone),
                toUtc(dto.preferredEndDateTime(), sourceZone),
                dto.isUrgent(),
                dto.notes(),
                dto.interviewType(),
                dto.interviewCoordinatorId(),
                dto.interviewCoordinatorDepartmentId()
        );
    }

    public static CreatePanelInterviewDto toUtc(CreatePanelInterviewDto dto, ZoneId sourceZone) {
        return new CreatePanelInterviewDto(
                dto.candidateId(),
                dto.candidateName(),
                dto.candidateEmail(),
                dto.candidateDesignationId(),
                toUtc(dto.startDateTime(), sourceZone),
                toUtc(dto.endDateTime(), sourceZone),
                dto.availabilitySlotIds(),
                dto.requiredTechnologyIds(),
                dto.isUrgent(),
                dto.notes(),
                dto.interviewType(),
                dto.interviewCoordinatorId(),
                dto.interviewCoordinatorDepartmentId()
        );
    }
}
