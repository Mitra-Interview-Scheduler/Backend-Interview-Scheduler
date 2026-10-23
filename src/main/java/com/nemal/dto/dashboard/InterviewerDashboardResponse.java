package com.nemal.dto.dashboard;

import java.time.LocalDateTime;
import java.util.List;

public record InterviewerDashboardResponse(
        ActionQueue actionQueue,
        LiveStatus liveStatus,
        Trends trends,
        LocalDateTime generatedAt
) {
    /**
     * @param feedbackOverdueCount all of this interviewer's interviews more than 24h past without
     *                             feedback (not just the ones in the {@code feedbackDue} preview)
     */
    public record ActionQueue(
            CountedList<FeedbackDueItem> feedbackDue,
            long feedbackOverdueCount,
            CountedList<AssessmentWaitingItem> assessmentsToReview,
            CountedList<MyPostponeItem> myPostponeProposals,
            CountedList<PanelFeedbackSharedItem> panelFeedbackShared
    ) {}


    public record FeedbackDueItem(
            Long scheduleId,
            String candidateName,
            String formName,
            String interviewType,
            LocalDateTime endedAt,
            double hoursSince,
            String meetingLink
    ) {}

    public record AssessmentWaitingItem(
            Long scheduleId,
            Long candidateId,
            String candidateName,
            String interviewType,
            String phase,
            long agingDays,
            LocalDateTime dueEnd,
            int reviewerCount
    ) {}

    public record MyPostponeItem(
            Long id,
            String status,
            String candidateName,
            LocalDateTime createdAt,
            LocalDateTime reviewedAt
    ) {}

    public record PanelFeedbackSharedItem(
            Long scheduleId,
            Long panelId,
            String candidateName,
            String submittedBy,
            LocalDateTime submittedAt,
            boolean unread
    ) {}

    public record LiveStatus(
            CountedList<UpcomingWithPrepItem> upcomingInterviews,
            CalendarHealth calendarHealth,
            AvailabilityCoverage availabilityCoverage
    ) {}

    public record UpcomingWithPrepItem(
            Long scheduleId,
            Long requestId,
            Long panelId,
            Long candidateId,
            String candidateName,
            LocalDateTime start,
            LocalDateTime end,
            String interviewType,
            List<String> interviewerNames,
            String meetingLink,
            boolean isPanel,
            boolean hasCalendarConflict,
            List<String> candidateSkills,
            List<String> candidateDomains,
            List<String> myCoreSkills,
            List<String> myCanDoSkills,
            List<String> panelMembers,
            Long cvDocumentId
    ) {}

    public record CalendarHealth(
            boolean connected,
            String googleAccountEmail,
            LocalDateTime lastUpdatedAt,
            LocalDateTime tokenExpiresAt,
            boolean tokenExpiringSoon,
            int selectedCalendarCount,
            String appCalendarId
    ) {}

    public record AvailabilityCoverage(
            long myOpenSlots,
            long myBookedSlots,
            double teamAverageOpenSlots,
            LocalDateTime lastPublishedSlotEnd,
            String nudgeMessage,
            int coverageDays
    ) {}

    public record Trends(
            InterviewsConducted interviewsConducted,
            FeedbackTurnaround feedbackTurnaround,
            List<RecommendationItem> recommendationDistribution,
            long reschedulesInitiated
    ) {}

    public record InterviewsConducted(
            long thisMonth,
            long thisQuarter,
            List<TypeCount> byType
    ) {}

    public record TypeCount(String interviewType, long count) {}

    public record FeedbackTurnaround(Double myMedianHours, Double teamMedianHours) {}

    public record RecommendationItem(String label, long count) {}
}
