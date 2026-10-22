package com.nemal.dto.dashboard;

import java.time.LocalDateTime;
import java.util.List;

public record AdminDashboardResponse(
        Alerts alerts,
        LiveStatus liveStatus,
        Trends trends,
        LocalDateTime generatedAt
) {
    public record Alerts(
            List<MissingFormAlert> interviewTypesMissingForms,
            CountedList<UserAlert> interviewersWithoutCalendar,
            CountedList<UserAlert> interviewersNoAvailability,
            CountedList<UserAlert> usersNoRoles,
            CountedList<InactiveCoordinatorAlert> inactiveCoordinatorsOnActiveCandidates,
            CountOnly emailFailuresLast24h,
            CatalogGaps catalogGaps
    ) {}

    public record MissingFormAlert(String interviewTypeCode, String interviewTypeLabel, String reason) {}

    public record UserAlert(Long userId, String name, String email) {}

    public record InactiveCoordinatorAlert(Long userId, String name, long activeCandidateCount) {}

    public record CountOnly(long count) {}

    public record CatalogGaps(
            List<String> designationsWithoutInterviewers,
            List<String> orphanCandidateTechnologies,
            List<String> unusedDomains
    ) {}

    public record LiveStatus(
            ActivityToday activityToday,
            UsersByRole usersByRole,
            List<FormUsage> feedbackFormUsage,
            List<TypeCount> notificationsByType,
            double emailOptOutRate
    ) {}

    public record ActivityToday(
            long interviewsToday,
            long feedbackSubmittedToday,
            long candidatesAddedToday,
            long notificationsSentToday
    ) {}

    public record UsersByRole(
            long adminActive,
            long adminInactive,
            long hrActive,
            long hrInactive,
            long interviewerActive,
            long interviewerInactive,
            long multiRoleCount,
            List<RecentUser> recentlyAdded
    ) {}

    public record RecentUser(Long userId, String name, String email, LocalDateTime createdAt) {}

    public record FormUsage(Long formId, String name, int version, boolean active, long submissionCount) {}

    public record TypeCount(String type, long count) {}

    public record Trends(
            List<InterviewerLoad> interviewerLoad,
            List<DepartmentFunnel> departmentFunnel,
            FeedbackQuality feedbackQuality,
            CalendarConflictBlocks calendarConflictBlocks,
            DocumentUploads documentUploads
    ) {}

    public record InterviewerLoad(Long userId, String name, long interviewCount, String flag) {}

    public record DepartmentFunnel(
            Long departmentId,
            String departmentName,
            long candidates,
            long interviews,
            long offers,
            long accepted
    ) {}

    public record FeedbackQuality(Double avgTurnaroundHours, Double panelDisagreementRate) {}

    public record CalendarConflictBlocks(String note, long count) {}

    public record DocumentUploads(long last30Days) {}
}
