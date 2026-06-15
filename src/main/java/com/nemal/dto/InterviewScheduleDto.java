package com.nemal.dto;

import com.nemal.entity.InterviewSchedule;
import com.nemal.enums.InterviewStatus;
import com.nemal.enums.InterviewType;
import java.time.LocalDateTime;

public record InterviewScheduleDto(
        Long id,
        Long requestId,
        Long interviewerId,
        String interviewerName,
        Long candidateId,
        String candidateName,
        LocalDateTime startDateTime,
        LocalDateTime endDateTime,
        String meetingLink,
        String location,
        InterviewStatus status,
        InterviewType interviewType,
        LocalDateTime completedAt
) {
    public static InterviewScheduleDto from(InterviewSchedule schedule) {
        return new InterviewScheduleDto(
                schedule.getId(),
                schedule.getRequest() != null ? schedule.getRequest().getId() : null,
                schedule.getInterviewer().getId(),
                schedule.getInterviewer().getFullName(),
                schedule.getRequest() != null ? schedule.getRequest().getId() : null,
                schedule.getRequest() != null ? schedule.getRequest().getCandidateName() : null,
                schedule.getStartDateTime(),
                schedule.getEndDateTime(),
                schedule.getMeetingLink(),
                schedule.getLocation(),
                schedule.getStatus(),
                schedule.getInterviewType(),
                schedule.getCompletedAt()
        );
    }
}