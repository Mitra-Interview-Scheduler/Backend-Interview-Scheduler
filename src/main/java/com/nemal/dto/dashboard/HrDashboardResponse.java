package com.nemal.dto.dashboard;

import java.time.LocalDateTime;
import java.util.List;

public record HrDashboardResponse(
        ActionQueue actionQueue,
        LiveStatus liveStatus,
        Trends trends,
        LocalDateTime generatedAt
) {
    public record ActionQueue(
            CountedList<PostponeItem> postponeRequests,
            CountedList<FeedbackOverdueItem> feedbackOverdue,
            CountedList<StalledCandidateItem> candidatesStalled,
            AssessmentsWaiting assessmentsWaiting,
            CountedList<OfferPendingItem> offersPending,
            CountedList<ScreeningBacklogItem> screeningBacklog
    ) {}

    public record AssessmentsWaiting(
            CountedList<AssessmentWaitingItem> awaitingReceived,
            CountedList<AssessmentWaitingItem> awaitingReviewer,
            CountedList<AssessmentWaitingItem> reviewIncomplete
    ) {}

    public record PostponeItem(
            Long id,
            Long scheduleId,
            Long candidateId,
            String candidateName,
            String interviewerName,
            LocalDateTime currentStart,
            LocalDateTime currentEnd,
            LocalDateTime proposedStart,
            LocalDateTime proposedEnd,
            String reason,
            LocalDateTime createdAt,
            double ageHours,
            Long panelId
    ) {}

    public record FeedbackOverdueItem(
            Long scheduleId,
            Long requestId,
            Long candidateId,
            String candidateName,
            Long interviewerId,
            String interviewerName,
            String interviewType,
            LocalDateTime completedAtOrEnd,
            double hoursOverdue,
            String meetingLink,
            long nudgeCount,
            LocalDateTime lastNudgedAt,
            String lastNudgedByName
    ) {}

    public record StalledCandidateItem(
            Long candidateId,
            String name,
            String statusKey,
            String statusLabel,
            String departmentName,
            String coordinatedHrName,
            LocalDateTime lastStatusChangeAt,
            long daysStalled
    ) {}

    public record AssessmentWaitingItem(
            Long scheduleId,
            Long candidateId,
            String candidateName,
            String interviewType,
            String phase,
            long agingDays,
            LocalDateTime dueEnd,
            int reviewerCount,
            String nextAction,
            String departmentName
    ) {}

    public record OfferPendingItem(
            Long candidateId,
            String name,
            String statusKey,
            String departmentName,
            long daysInOffer,
            String coordinatedHrName
    ) {}

    public record ScreeningBacklogItem(
            Long candidateId,
            String name,
            String statusKey,
            LocalDateTime appliedAt,
            long daysWaiting,
            String coordinatedHrName
    ) {}

    public record LiveStatus(
            CountedList<UpcomingInterviewItem> upcomingInterviews,
            List<PipelineFunnelItem> pipelineFunnel,
            List<CapacityDay> interviewerCapacity,
            List<UnfilledNeedItem> unfilledNeeds
    ) {}

    public record UpcomingInterviewItem(
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
            boolean hasCalendarConflict
    ) {}

    public record PipelineFunnelItem(
            String statusKey,
            String label,
            long count,
            int stepOrder
    ) {}

    public record CapacityDay(
            String date,
            Long departmentId,
            String departmentName,
            long availableSlots,
            long bookedSlots
    ) {}

    public record UnfilledNeedItem(
            Long candidateId,
            String name,
            String statusKey,
            String departmentName,
            String designationName,
            String reason
    ) {}

    public record Trends(
            List<TimeInStageItem> timeInStage,
            TimeToSchedule timeToSchedule,
            List<CloseReasonItem> closeReasons,
            OfferMetrics offerMetrics,
            List<CoordinatorWorkloadItem> coordinatorWorkload,
            RescheduleRate rescheduleRate
    ) {}

    public record TimeInStageItem(
            String statusKey,
            String label,
            double medianDays,
            double p90Days,
            int sampleSize
    ) {}

    public record TimeToSchedule(
            Double overallMedianHours,
            List<NamedMetric> byDepartment,
            List<NamedMetric> byTier
    ) {}

    public record NamedMetric(String name, double medianHours, int sampleSize) {}

    public record CloseReasonItem(String code, String label, long count) {}

    public record OfferMetrics(
            double acceptanceRate,
            Double medianOfferToCloseDays,
            long offersMade,
            long offersAccepted
    ) {}

    public record CoordinatorWorkloadItem(
            Long userId,
            String name,
            long candidatesCreated,
            long candidatesCoordinated
    ) {}

    public record RescheduleRate(
            double overallPercent,
            List<NamedCount> byInterviewer,
            List<NamedCount> byCandidate
    ) {}

    public record NamedCount(Long id, String name, long count) {}
}
