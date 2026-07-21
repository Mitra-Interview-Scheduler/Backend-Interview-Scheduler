package com.nemal.dto;

import com.nemal.entity.InterviewRequest;
import com.nemal.entity.InterviewSchedule;
import com.nemal.entity.InterviewPanel;
import com.nemal.enums.InterviewStatus;
import com.nemal.enums.RequestStatus;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

public record InterviewRequestDto(
        Long id,
        String candidateName,
        Long candidateId,
        Long candidateDesignationId,
        String candidateDesignationName,
        List<TechnologySimpleDto> requiredTechnologies,
        LocalDateTime preferredStartDateTime,
        LocalDateTime preferredEndDateTime,
        Long requestedById,
        String requestedByName,
        Long assignedInterviewerId,
        String assignedInterviewerName,
        String assignedInterviewerDesignationName,
        Long interviewCoordinatorId,
        String interviewCoordinatorName,
        String coordinatedHrName,
        Long availabilitySlotId,
        Long panelId,
        RequestStatus status,
        LocalDateTime respondedAt,
        String responseNotes,
        boolean isUrgent,
        String notes,
        LocalDateTime createdAt,
        Long interviewScheduleId,
        InterviewStatus interviewStatus,
        String interviewType,
        LocalDateTime scheduledStartDateTime,
        LocalDateTime scheduledEndDateTime,
        LocalDateTime interviewCompletedAt,
        String meetingLink,
        String googleCalendarEventId
) {
    public static InterviewRequestDto from(InterviewRequest request) {
        InterviewSchedule schedule = request.getInterviewSchedule();
        return new InterviewRequestDto(
                request.getId(),
                request.getCandidateName(),
                request.getCandidate() != null ? request.getCandidate().getId() : null,
                request.getCandidateDesignation() != null ? request.getCandidateDesignation().getId() : null,
                request.getCandidateDesignation() != null ? request.getCandidateDesignation().getName() : null,
                request.getRequiredTechnologies() != null
                        ? request.getRequiredTechnologies().stream()
                        .map(t -> new TechnologySimpleDto(
                                t.getId(),
                                t.getCode(),
                                t.getName(),
                                t.getCategory().getCode(),
                                t.getCategory().getLabel()
                        ))
                        .collect(Collectors.toList())
                        : List.of(),
                request.getPreferredStartDateTime(),
                request.getPreferredEndDateTime(),
                request.getRequestedBy() != null ? request.getRequestedBy().getId() : null,
                request.getRequestedBy() != null ? request.getRequestedBy().getFullName() : null,
                request.getAssignedInterviewer() != null ? request.getAssignedInterviewer().getId() : null,
                request.getAssignedInterviewer() != null ? request.getAssignedInterviewer().getFullName() : null,
                request.getAssignedInterviewer() != null
                        && request.getAssignedInterviewer().getCurrentDesignation() != null
                        ? request.getAssignedInterviewer().getCurrentDesignation().getName()
                        : null,
                request.getInterviewCoordinator() != null ? request.getInterviewCoordinator().getId() : null,
                request.getInterviewCoordinator() != null ? request.getInterviewCoordinator().getFullName().trim() : null,
                request.getCandidate() != null && request.getCandidate().getCoordinatedHr() != null
                        ? request.getCandidate().getCoordinatedHr().getFullName().trim()
                        : null,
                request.getAvailabilitySlot() != null ? request.getAvailabilitySlot().getId() : null,
                request.getPanel() != null ? request.getPanel().getId() : null,
                request.getStatus(),
                request.getRespondedAt(),
                request.getResponseNotes(),
                request.isUrgent(),
                request.getNotes(),
                request.getCreatedAt(),
                schedule != null ? schedule.getId() : null,
                schedule != null ? schedule.getStatus() : null,
                schedule != null ? schedule.getInterviewType() : null,
                schedule != null ? schedule.getStartDateTime() : null,
                schedule != null ? schedule.getEndDateTime() : null,
                schedule != null ? schedule.getCompletedAt() : null,
                schedule != null && schedule.getStatus() == InterviewStatus.SCHEDULED
                        ? resolveMeetingLink(schedule, request)
                        : null,
                schedule != null ? schedule.getGoogleCalendarEventId() : null
        );
    }

    private static String resolveMeetingLink(InterviewSchedule schedule, InterviewRequest request) {
        if (schedule == null || schedule.getStatus() != InterviewStatus.SCHEDULED) {
            return null;
        }
        String link = schedule.getMeetingLink();
        if (link != null && !link.isBlank()) {
            return link;
        }
        InterviewPanel panel = request != null ? request.getPanel() : null;
        if (panel != null && panel.getMeetingLink() != null && !panel.getMeetingLink().isBlank()) {
            return panel.getMeetingLink();
        }
        return null;
    }
}