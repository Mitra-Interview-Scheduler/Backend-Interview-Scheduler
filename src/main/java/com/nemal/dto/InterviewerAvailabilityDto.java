package com.nemal.dto;

import com.nemal.entity.AvailabilitySlot;
import com.nemal.entity.Designation;
import com.nemal.entity.InterviewRequest;
import com.nemal.entity.Tier;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

/**
 * DTO returned to the HR calendar.
 *
 * Added in latest version:
 *   • interviewerTierOrder  — Tier.tierOrder of the interviewer's current designation
 *   • interviewerLevelOrder — Designation.levelOrder of the interviewer
 *
 * The frontend uses these two fields to enforce the privilege rule:
 *   interviewer must be at a strictly higher tier, OR same tier + strictly higher level,
 *   than the candidate's target designation before a booking is allowed.
 */
public record InterviewerAvailabilityDto(
        Long slotId,
        Long interviewerId,
        String interviewerName,
        String department,
        String designation,
        Integer yearsOfExperience,
        List<String> technologies,
        LocalDateTime startDateTime,
        LocalDateTime endDateTime,
        String status,
        String candidateName,
        Long requestId,              // ID of the InterviewRequest that booked this slot
        Integer interviewerTierOrder,  // NEW — Tier.tierOrder for the interviewer
        Integer interviewerLevelOrder,  // NEW — Designation.levelOrder for the interviewer
        String interviewType,         // TECHNICAL | HR — from the booked InterviewSchedule
        Long interviewScheduleId,
        String interviewStatus        // SCHEDULED | COMPLETED | CANCELLED — from InterviewSchedule
) {

    public static InterviewerAvailabilityDto from(AvailabilitySlot slot) {
        // ── Resolve candidateName + requestId ────────────────────────────────
        String candidateName = null;
        Long requestId = null;
        String interviewType = null;
        Long interviewScheduleId = null;
        String interviewStatus = null;

        if (slot.getInterviewSchedule() != null) {
            interviewScheduleId = slot.getInterviewSchedule().getId();
            if (slot.getInterviewSchedule().getStatus() != null) {
                interviewStatus = slot.getInterviewSchedule().getStatus().name();
            }
            if (slot.getInterviewSchedule().getInterviewType() != null) {
                interviewType = slot.getInterviewSchedule().getInterviewType().name();
            }
            InterviewRequest req = slot.getInterviewSchedule().getRequest();
            if (req != null) {
                candidateName = req.getCandidateName();
                requestId = req.getId();
            }
        }

        // Fallback: parse "Interview: <name>" / "Panel Interview: <name>" from description
        if (candidateName == null && slot.getDescription() != null) {
            String desc = slot.getDescription();
            if (desc.startsWith("Interview: ")) {
                candidateName = desc.substring("Interview: ".length());
            } else if (desc.startsWith("Panel Interview: ")) {
                candidateName = desc.substring("Panel Interview: ".length());
            }
        }

        // ── Technologies ─────────────────────────────────────────────────────
        List<String> techs = List.of();
        if (slot.getInterviewer() != null
                && slot.getInterviewer().getInterviewerTechnologies() != null) {
            techs = slot.getInterviewer().getInterviewerTechnologies().stream()
                    .filter(it -> it != null && it.isActive() && it.getTechnology() != null)
                    .map(it -> it.getTechnology().getName())
                    .collect(Collectors.toList());
        }

        // ── Tier / level for privilege check ─────────────────────────────────
        Integer tierOrder  = null;
        Integer levelOrder = null;

        if (slot.getInterviewer() != null) {
            Designation desig = slot.getInterviewer().getCurrentDesignation();
            if (desig != null) {
                levelOrder = desig.getLevelOrder();
                Tier tier = desig.getTier();
                if (tier != null) {
                    tierOrder = tier.getTierOrder();
                }
            }
        }

        return new InterviewerAvailabilityDto(
                slot.getId(),
                slot.getInterviewer() != null ? slot.getInterviewer().getId() : null,
                slot.getInterviewer() != null ? slot.getInterviewer().getFullName() : "Unknown",
                slot.getInterviewer() != null && slot.getInterviewer().getDepartment() != null
                        ? slot.getInterviewer().getDepartment().getName() : null,
                slot.getInterviewer() != null && slot.getInterviewer().getCurrentDesignation() != null
                        ? slot.getInterviewer().getCurrentDesignation().getName() : null,
                slot.getInterviewer() != null ? slot.getInterviewer().getYearsOfExperience() : null,
                techs,
                slot.getStartDateTime(),
                slot.getEndDateTime(),
                slot.getStatus() != null ? slot.getStatus().name() : "AVAILABLE",
                candidateName,
                requestId,
                tierOrder,    // interviewerTierOrder
                levelOrder,   // interviewerLevelOrder
                interviewType,
                interviewScheduleId,
                interviewStatus
        );
    }
}