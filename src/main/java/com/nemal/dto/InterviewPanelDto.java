package com.nemal.dto;

import com.nemal.entity.InterviewPanel;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

public record InterviewPanelDto(
        Long id,
        Long candidateId,
        String candidateName,
        LocalDateTime startDateTime,
        LocalDateTime endDateTime,
        List<InterviewRequestDto> panelRequests,
        boolean isUrgent,
        String notes,
        Long requestedById,
        String requestedByName,
        String interviewCoordinatorName,
        LocalDateTime createdAt
) {
    public static InterviewPanelDto from(InterviewPanel panel) {
        return new InterviewPanelDto(
                panel.getId(),
                panel.getCandidate() != null ? panel.getCandidate().getId() : null,
                panel.getCandidateName(),
                panel.getStartDateTime(),
                panel.getEndDateTime(),
                panel.getPanelRequests() != null
                        ? panel.getPanelRequests().stream()
                        .map(InterviewRequestDto::from)
                        .collect(Collectors.toList())
                        : List.of(),
                panel.isUrgent(),
                panel.getNotes(),
                panel.getRequestedBy() != null ? panel.getRequestedBy().getId() : null,
                panel.getRequestedBy() != null ? panel.getRequestedBy().getFullName() : null,
                panel.getInterviewCoordinator() != null ? panel.getInterviewCoordinator().getFullName().trim() : null,
                panel.getCreatedAt()
        );
    }
}