package com.nemal.dto;

import com.nemal.entity.InterviewRequest;
import com.nemal.entity.InterviewSchedule;
import com.nemal.enums.InterviewStatus;
import com.nemal.enums.RequestStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * DTO for InterviewRequest - avoids lazy-loading issues by only including
 * necessary fields and simple types. No relationships to other entities.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class InterviewRequestSimpleDto {
    private Long id;
    private String candidateName;
    private Long candidateId;
    private Long candidateDesignationId;
    private LocalDateTime preferredStartDateTime;
    private LocalDateTime preferredEndDateTime;
    private Long requestedById;
    private String requestedByName;
    private Long assignedInterviewerId;
    private String assignedInterviewerName;
    private Long availabilitySlotId;
    private RequestStatus status;
    private LocalDateTime respondedAt;
    private String responseNotes;
    private boolean isUrgent;
    private String notes;
    private Long interviewScheduleId;
    private InterviewStatus interviewStatus;
    private String interviewType;
    private LocalDateTime scheduledStartDateTime;
    private LocalDateTime scheduledEndDateTime;
    private LocalDateTime interviewCompletedAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    /**
     * Convert InterviewRequest entity to DTO without triggering lazy loads
     */
    public static InterviewRequestSimpleDto from(InterviewRequest request) {
        InterviewSchedule schedule = request.getInterviewSchedule();
        return InterviewRequestSimpleDto.builder()
                .id(request.getId())
                .candidateName(request.getCandidateName())
                .candidateId(request.getCandidate() != null ? request.getCandidate().getId() : null)
                .candidateDesignationId(request.getCandidateDesignation() != null ? request.getCandidateDesignation().getId() : null)
                .preferredStartDateTime(request.getPreferredStartDateTime())
                .preferredEndDateTime(request.getPreferredEndDateTime())
                .requestedById(request.getRequestedBy() != null ? request.getRequestedBy().getId() : null)
                .requestedByName(request.getRequestedBy() != null ? request.getRequestedBy().getFullName() : null)
                .assignedInterviewerId(request.getAssignedInterviewer() != null ? request.getAssignedInterviewer().getId() : null)
                .assignedInterviewerName(request.getAssignedInterviewer() != null ? request.getAssignedInterviewer().getFullName() : null)
                .availabilitySlotId(request.getAvailabilitySlot() != null ? request.getAvailabilitySlot().getId() : null)
                .status(request.getStatus())
                .respondedAt(request.getRespondedAt())
                .responseNotes(request.getResponseNotes())
                .isUrgent(request.isUrgent())
                .notes(request.getNotes())
                .interviewScheduleId(schedule != null ? schedule.getId() : null)
                .interviewStatus(schedule != null ? schedule.getStatus() : null)
                .interviewType(schedule != null && schedule.getInterviewType() != null ? schedule.getInterviewType().name() : null)
                .scheduledStartDateTime(schedule != null ? schedule.getStartDateTime() : null)
                .scheduledEndDateTime(schedule != null ? schedule.getEndDateTime() : null)
                .interviewCompletedAt(schedule != null ? schedule.getCompletedAt() : null)
                .createdAt(request.getCreatedAt())
                .updatedAt(request.getUpdatedAt())
                .build();
    }
}
