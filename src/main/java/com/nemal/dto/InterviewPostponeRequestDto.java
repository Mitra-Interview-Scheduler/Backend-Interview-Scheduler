package com.nemal.dto;

import com.nemal.entity.InterviewPostponeRequest;
import com.nemal.entity.InterviewRequest;
import com.nemal.entity.InterviewSchedule;
import com.nemal.enums.PostponeRequestStatus;

import java.time.LocalDateTime;

public record InterviewPostponeRequestDto(
        Long id,
        Long interviewScheduleId,
        Long interviewRequestId,
        Long panelId,
        Long requestedById,
        String requestedByName,
        String reason,
        LocalDateTime preferredStartDateTime,
        LocalDateTime preferredEndDateTime,
        PostponeRequestStatus status,
        Long reviewedById,
        String reviewedByName,
        String reviewNotes,
        LocalDateTime createdAt,
        LocalDateTime resolvedAt,
        String candidateName,
        LocalDateTime interviewStartDateTime,
        LocalDateTime interviewEndDateTime,
        String position,
        String interviewerName
) {
    public static InterviewPostponeRequestDto from(InterviewPostponeRequest request) {
        InterviewSchedule schedule = request.getInterviewSchedule();
        InterviewRequest interviewRequest = request.getInterviewRequest();
        if (interviewRequest == null && schedule != null) {
            interviewRequest = schedule.getRequest();
        }

        String candidateName = interviewRequest != null ? interviewRequest.getCandidateName() : null;
        String position = resolvePosition(interviewRequest);
        String interviewerName = interviewRequest != null && interviewRequest.getAssignedInterviewer() != null
                ? interviewRequest.getAssignedInterviewer().getFullName()
                : (schedule != null && schedule.getInterviewer() != null
                        ? schedule.getInterviewer().getFullName()
                        : null);
        Long panelId = interviewRequest != null && interviewRequest.getPanel() != null
                ? interviewRequest.getPanel().getId()
                : null;

        return new InterviewPostponeRequestDto(
                request.getId(),
                schedule != null ? schedule.getId() : null,
                interviewRequest != null ? interviewRequest.getId() : null,
                panelId,
                request.getRequestedBy() != null ? request.getRequestedBy().getId() : null,
                request.getRequestedBy() != null ? request.getRequestedBy().getFullName() : null,
                request.getReason(),
                request.getPreferredStartDateTime(),
                request.getPreferredEndDateTime(),
                request.getStatus(),
                request.getReviewedBy() != null ? request.getReviewedBy().getId() : null,
                request.getReviewedBy() != null ? request.getReviewedBy().getFullName() : null,
                request.getReviewNotes(),
                request.getCreatedAt(),
                request.getResolvedAt(),
                candidateName,
                schedule != null ? schedule.getStartDateTime() : null,
                schedule != null ? schedule.getEndDateTime() : null,
                position,
                interviewerName
        );
    }

    private static String resolvePosition(InterviewRequest interviewRequest) {
        if (interviewRequest == null) {
            return null;
        }
        if (interviewRequest.getCandidateDesignation() != null
                && interviewRequest.getCandidateDesignation().getName() != null
                && !interviewRequest.getCandidateDesignation().getName().isBlank()) {
            return interviewRequest.getCandidateDesignation().getName().trim();
        }
        if (interviewRequest.getCandidate() != null
                && interviewRequest.getCandidate().getTargetDesignation() != null
                && interviewRequest.getCandidate().getTargetDesignation().getName() != null
                && !interviewRequest.getCandidate().getTargetDesignation().getName().isBlank()) {
            return interviewRequest.getCandidate().getTargetDesignation().getName().trim();
        }
        return null;
    }
}
