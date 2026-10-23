package com.nemal.service.dashboard;

import com.fasterxml.jackson.databind.JsonNode;
import com.nemal.dto.dashboard.CountedList;
import com.nemal.dto.dashboard.DashboardItemsResponse;
import com.nemal.dto.dashboard.DashboardPaging;
import com.nemal.dto.dashboard.InterviewerDashboardResponse;
import com.nemal.dto.dashboard.InterviewerDashboardResponse.*;
import com.nemal.entity.*;
import com.nemal.enums.AssessmentPhase;
import com.nemal.enums.InterviewStatus;
import com.nemal.enums.PostponeRequestStatus;
import com.nemal.enums.Role;
import com.nemal.repository.*;
import com.nemal.util.DashboardClock;
import com.nemal.util.TimeZoneMapper;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.*;

@Service
public class InterviewerDashboardService {

    private final InterviewScheduleRepository interviewScheduleRepository;
    private final FeedbackResponseRepository feedbackResponseRepository;
    private final AssessmentReviewerRepository assessmentReviewerRepository;
    private final InterviewPostponeRequestRepository postponeRequestRepository;
    private final UserGoogleCalendarCredentialsRepository calendarCredentialsRepository;
    private final AvailabilitySlotRepository availabilitySlotRepository;
    private final UserRepository userRepository;
    private final CandidateTechnologyRepository candidateTechnologyRepository;
    private final CandidateDomainRepository candidateDomainRepository;
    private final InterviewerTechnologyRepository interviewerTechnologyRepository;
    private final CandidateDocumentRepository candidateDocumentRepository;
    private final NotificationRepository notificationRepository;
    private final FeedbackFormRepository feedbackFormRepository;
    private final InterviewRequestRepository interviewRequestRepository;

    public InterviewerDashboardService(
            InterviewScheduleRepository interviewScheduleRepository,
            FeedbackResponseRepository feedbackResponseRepository,
            AssessmentReviewerRepository assessmentReviewerRepository,
            InterviewPostponeRequestRepository postponeRequestRepository,
            UserGoogleCalendarCredentialsRepository calendarCredentialsRepository,
            AvailabilitySlotRepository availabilitySlotRepository,
            UserRepository userRepository,
            CandidateTechnologyRepository candidateTechnologyRepository,
            CandidateDomainRepository candidateDomainRepository,
            InterviewerTechnologyRepository interviewerTechnologyRepository,
            CandidateDocumentRepository candidateDocumentRepository,
            NotificationRepository notificationRepository,
            FeedbackFormRepository feedbackFormRepository,
            InterviewRequestRepository interviewRequestRepository
    ) {
        this.interviewRequestRepository = interviewRequestRepository;
        this.interviewScheduleRepository = interviewScheduleRepository;
        this.feedbackResponseRepository = feedbackResponseRepository;
        this.assessmentReviewerRepository = assessmentReviewerRepository;
        this.postponeRequestRepository = postponeRequestRepository;
        this.calendarCredentialsRepository = calendarCredentialsRepository;
        this.availabilitySlotRepository = availabilitySlotRepository;
        this.userRepository = userRepository;
        this.candidateTechnologyRepository = candidateTechnologyRepository;
        this.candidateDomainRepository = candidateDomainRepository;
        this.interviewerTechnologyRepository = interviewerTechnologyRepository;
        this.candidateDocumentRepository = candidateDocumentRepository;
        this.notificationRepository = notificationRepository;
        this.feedbackFormRepository = feedbackFormRepository;
    }

    @Transactional(readOnly = true)
    public InterviewerDashboardResponse getDashboard(
            User user, int previewSize, int upcomingDays, int coverageDays, ZoneId zone) {
        LocalDateTime now = DashboardClock.nowUtc();
        Long interviewerId = user.getId();
        int preview = DashboardPaging.previewSize(previewSize);
        int upcomingWindow = DashboardPaging.upcomingDays(upcomingDays);
        int coverageWindow = DashboardPaging.upcomingDays(coverageDays);

        ActionQueue actionQueue = new ActionQueue(
                buildFeedbackDue(interviewerId, zone, now, 0, preview),
                interviewScheduleRepository.countFeedbackOverdue(
                        now.minusHours(DashboardPaging.DEFAULT_FEEDBACK_OVERDUE_HOURS), interviewerId, null),
                buildAssessmentsToReview(interviewerId, zone, now, 0, preview),
                buildMyPostponeProposals(interviewerId, zone, 0, preview),
                CountedList.empty()
        );

        LiveStatus liveStatus = new LiveStatus(
                buildUpcomingWithPrep(user, zone, now, upcomingWindow, 0, preview),
                buildCalendarHealth(interviewerId, zone, now),
                buildAvailabilityCoverage(interviewerId, zone, now, coverageWindow)
        );

        Trends trends = buildTrends(interviewerId, now, zone);

        return new InterviewerDashboardResponse(
                actionQueue, liveStatus, trends, TimeZoneMapper.fromUtc(now, zone));
    }

    @Transactional(readOnly = true)
    public AvailabilityCoverage getAvailabilityCoverage(User user, int coverageDays, ZoneId zone) {
        return buildAvailabilityCoverage(
                user.getId(), zone, DashboardClock.nowUtc(), DashboardPaging.upcomingDays(coverageDays));
    }

    @Transactional(readOnly = true)
    public DashboardItemsResponse getDashboardItems(
            User user, String section, Integer pageRaw, Integer sizeRaw, int upcomingDays, ZoneId zone) {
        int page = DashboardPaging.page(pageRaw);
        int size = DashboardPaging.size(sizeRaw);
        int upcomingWindow = DashboardPaging.upcomingDays(upcomingDays);
        LocalDateTime now = DashboardClock.nowUtc();
        Long interviewerId = user.getId();
        String key = section == null ? "" : section.trim();

        return switch (key) {
            case "feedbackDue" ->
                    DashboardItemsResponse.of(key, buildFeedbackDue(interviewerId, zone, now, page, size));
            case "assessmentsToReview" ->
                    DashboardItemsResponse.of(key, buildAssessmentsToReview(interviewerId, zone, now, page, size));
            case "myPostponeProposals" ->
                    DashboardItemsResponse.of(key, buildMyPostponeProposals(interviewerId, zone, page, size));
            case "panelFeedbackShared" ->
                    DashboardItemsResponse.of(key, buildPanelFeedbackShared(interviewerId, zone, page, size));
            case "upcomingInterviews" ->
                    DashboardItemsResponse.of(key, buildUpcomingWithPrep(user, zone, now, upcomingWindow, page, size));
            default -> throw new IllegalArgumentException("Unknown dashboard section");
        };
    }

    private CountedList<FeedbackDueItem> buildFeedbackDue(
            Long interviewerId, ZoneId zone, LocalDateTime now, int page, int size) {
        // Feedback becomes "due" an hour after the interview; only this interviewer's, paged in SQL.
        LocalDateTime cutoff = now.minusHours(1);
        List<InterviewSchedule> due = interviewScheduleRepository.findFeedbackOverdue(
                cutoff, interviewerId, null, PageRequest.of(page, size));
        long dueTotal = interviewScheduleRepository.countFeedbackOverdue(cutoff, interviewerId, null);
        // One form lookup per distinct (department, designation, type) on the page, not per item.
        Map<String, String> formNames = new HashMap<>();
        List<FeedbackDueItem> items = due.stream()
                .map(s -> {
                    LocalDateTime ended = s.getCompletedAt() != null ? s.getCompletedAt() : s.getEndDateTime();
                    Long deptId = s.getRequest() != null && s.getRequest().getCandidate() != null
                            && s.getRequest().getCandidate().getDepartment() != null
                            ? s.getRequest().getCandidate().getDepartment().getId() : null;
                    Long desigId = s.getRequest() != null && s.getRequest().getCandidate() != null
                            && s.getRequest().getCandidate().getTargetDesignation() != null
                            ? s.getRequest().getCandidate().getTargetDesignation().getId() : null;
                    String formKey = deptId + "|" + desigId + "|" + s.getInterviewType();
                    String formName = formNames.computeIfAbsent(formKey, k -> {
                        try {
                            List<FeedbackForm> forms = feedbackFormRepository
                                    .findActiveFormsByDepartmentDesignationAndInterviewType(
                                            deptId, desigId, s.getInterviewType());
                            return forms.isEmpty() ? null : forms.get(0).getName();
                        } catch (Exception ignored) {
                            return null; // best-effort
                        }
                    });
                    return new FeedbackDueItem(
                            s.getId(),
                            resolveCandidateName(s),
                            formName,
                            s.getInterviewType(),
                            TimeZoneMapper.fromUtc(ended, zone),
                            hoursBetween(ended != null ? ended : now, now),
                            s.getMeetingLink()
                    );
                })
                .toList();
        return CountedList.fromPage(items, (int) dueTotal, page, size);
    }

    private CountedList<AssessmentWaitingItem> buildAssessmentsToReview(
            Long interviewerId, ZoneId zone, LocalDateTime now, int page, int size) {
        // Phase and "not yet reviewed by me" are filtered in SQL; reviewer counts in one grouped query.
        List<AssessmentReviewer> assignments =
                assessmentReviewerRepository.findOpenAssignmentsForReviewer(interviewerId);
        List<AssessmentReviewer> pageRows = DashboardPaging.slice(assignments, page, size);
        Map<Long, Integer> reviewerCounts = new HashMap<>();
        List<Long> pageScheduleIds = pageRows.stream().map(ar -> ar.getInterviewSchedule().getId()).toList();
        if (!pageScheduleIds.isEmpty()) {
            for (Object[] row : assessmentReviewerRepository.countByScheduleIds(pageScheduleIds)) {
                reviewerCounts.put((Long) row[0], ((Number) row[1]).intValue());
            }
        }
        List<AssessmentWaitingItem> items = new ArrayList<>();
        for (AssessmentReviewer ar : pageRows) {
            InterviewSchedule s = ar.getInterviewSchedule();
            Candidate c = s.getRequest() != null ? s.getRequest().getCandidate() : null;
            LocalDateTime due = s.getEndDateTime() != null ? s.getEndDateTime() : s.getStartDateTime();
            long aging = due != null ? Math.max(0, DashboardClock.localDaysBetween(due, now, zone)) : 0;
            int reviewerCount = reviewerCounts.getOrDefault(s.getId(), 0);
            items.add(new AssessmentWaitingItem(
                    s.getId(),
                    c != null ? c.getId() : null,
                    resolveCandidateName(s),
                    s.getInterviewType(),
                    s.getAssessmentPhase().name(),
                    aging,
                    TimeZoneMapper.fromUtc(due, zone),
                    reviewerCount
            ));
        }
        return CountedList.fromPage(items, assignments.size(), page, size);
    }

    private CountedList<MyPostponeItem> buildMyPostponeProposals(
            Long interviewerId, ZoneId zone, int page, int size) {
        List<InterviewPostponeRequest> all =
                postponeRequestRepository.findByRequestedByIdWithDetails(interviewerId);
        List<MyPostponeItem> items = DashboardPaging.slice(all, page, size).stream()
                .map(p -> {
                    String candidateName = null;
                    if (p.getInterviewRequest() != null) {
                        Candidate c = p.getInterviewRequest().getCandidate();
                        candidateName = c != null ? c.getName() : p.getInterviewRequest().getCandidateName();
                    }
                    LocalDateTime reviewedAt = p.getResolvedAt() != null ? p.getResolvedAt()
                            : (p.getStatus() != PostponeRequestStatus.PENDING ? p.getUpdatedAt() : null);
                    return new MyPostponeItem(
                            p.getId(),
                            p.getStatus() != null ? p.getStatus().name() : null,
                            candidateName,
                            TimeZoneMapper.fromUtc(p.getCreatedAt(), zone),
                            TimeZoneMapper.fromUtc(reviewedAt, zone)
                    );
                })
                .toList();
        return CountedList.fromPage(items, all.size(), page, size);
    }

    private CountedList<PanelFeedbackSharedItem> buildPanelFeedbackShared(
            Long interviewerId, ZoneId zone, int page, int size) {
        LocalDateTime from = DashboardClock.nowUtc().minusDays(30);
        List<InterviewSchedule> upcoming = interviewScheduleRepository.findUpcomingForInterviewer(
                interviewerId, from, DashboardClock.nowUtc().plusDays(1));
        Set<Long> panelIds = new LinkedHashSet<>();
        Map<Long, InterviewSchedule> mySchedulesByPanel = new HashMap<>();
        for (InterviewSchedule s : upcoming) {
            if (s.getRequest() != null && s.getRequest().getPanel() != null) {
                Long panelId = s.getRequest().getPanel().getId();
                panelIds.add(panelId);
                mySchedulesByPanel.put(panelId, s);
            }
        }
        // Only this interviewer's own panels with feedback still outstanding (scoped in SQL).
        List<InterviewSchedule> overdue = interviewScheduleRepository.findFeedbackOverdue(
                DashboardClock.nowUtc(), interviewerId, null, Pageable.unpaged());
        for (InterviewSchedule s : overdue) {
            if (s.getRequest() != null && s.getRequest().getPanel() != null) {
                Long panelId = s.getRequest().getPanel().getId();
                panelIds.add(panelId);
                mySchedulesByPanel.putIfAbsent(panelId, s);
            }
        }
        if (panelIds.isEmpty()) {
            return CountedList.empty();
        }

        // One query for every panel's feedback, one grouped query for unread flags.
        List<Object[]> panelFeedback = feedbackResponseRepository.findByPanelIdsOrderBySubmittedAtDesc(panelIds);
        Set<Long> candidateIds = new HashSet<>();
        for (InterviewSchedule mine : mySchedulesByPanel.values()) {
            if (mine.getRequest() != null && mine.getRequest().getCandidate() != null) {
                candidateIds.add(mine.getRequest().getCandidate().getId());
            }
        }
        Set<Long> unreadCandidates = new HashSet<>();
        if (!candidateIds.isEmpty()) {
            for (Object[] row : notificationRepository.countUnreadByRecipientTypeAndEntities(
                    interviewerId, "FEEDBACK_SUBMITTED", candidateIds)) {
                if (row[0] != null && row[1] != null && ((Number) row[1]).longValue() > 0) {
                    unreadCandidates.add((Long) row[0]);
                }
            }
        }

        List<PanelFeedbackSharedItem> items = new ArrayList<>();
        for (Object[] row : panelFeedback) {
            Long panelId = (Long) row[0];
            FeedbackResponse fr = (FeedbackResponse) row[1];
            if (fr.getInterviewer() != null && interviewerId.equals(fr.getInterviewer().getId())) {
                continue;
            }
            InterviewSchedule mySchedule = mySchedulesByPanel.get(panelId);
            Long scheduleId = fr.getInterviewSchedule() != null
                    ? fr.getInterviewSchedule().getId()
                    : (mySchedule != null ? mySchedule.getId() : null);
            String candidateName = mySchedule != null ? resolveCandidateName(mySchedule) : null;
            Long candidateId = mySchedule != null && mySchedule.getRequest() != null
                    && mySchedule.getRequest().getCandidate() != null
                    ? mySchedule.getRequest().getCandidate().getId() : null;
            items.add(new PanelFeedbackSharedItem(
                    scheduleId,
                    panelId,
                    candidateName,
                    fr.getInterviewer() != null ? fr.getInterviewer().getFullName() : null,
                    TimeZoneMapper.fromUtc(fr.getSubmittedAt(), zone),
                    candidateId != null && unreadCandidates.contains(candidateId)
            ));
        }
        return CountedList.page(items, page, size);
    }

    private CountedList<UpcomingWithPrepItem> buildUpcomingWithPrep(
            User user, ZoneId zone, LocalDateTime now, int upcomingDays, int page, int size) {
        LocalDateTime from = now;
        LocalDateTime to = DashboardPaging.upcomingWindowEnd(now, upcomingDays, zone);
        List<InterviewSchedule> schedules = interviewScheduleRepository
                .findUpcomingForInterviewer(user.getId(), from, to).stream()
                .filter(s -> s.getStatus() == InterviewStatus.SCHEDULED)
                .toList();

        List<InterviewerTechnology> myTechs =
                interviewerTechnologyRepository.findByInterviewerIdAndIsActiveTrue(user.getId());
        List<String> myCore = myTechs.stream()
                .filter(InterviewerTechnology::isCore)
                .map(t -> t.getTechnology() != null ? t.getTechnology().getName() : null)
                .filter(Objects::nonNull)
                .toList();
        List<String> myCanDo = myTechs.stream()
                .filter(t -> !t.isCore())
                .map(t -> t.getTechnology() != null ? t.getTechnology().getName() : null)
                .filter(Objects::nonNull)
                .toList();

        // Prep data and panel members for every interview on the page in a few batch queries
        // (was: 3 queries per interview plus lazy panel loads).
        List<InterviewSchedule> pageRows = DashboardPaging.slice(schedules, page, size);
        Set<Long> candidateIds = new HashSet<>();
        Set<Long> panelIds = new HashSet<>();
        for (InterviewSchedule s : pageRows) {
            InterviewRequest req = s.getRequest();
            if (req != null && req.getCandidate() != null) candidateIds.add(req.getCandidate().getId());
            if (req != null && req.getPanel() != null) panelIds.add(req.getPanel().getId());
        }
        Map<Long, List<String>> skillsByCandidate = new HashMap<>();
        Map<Long, List<String>> domainsByCandidate = new HashMap<>();
        Map<Long, Long> cvByCandidate = new HashMap<>();
        if (!candidateIds.isEmpty()) {
            for (Object[] row : candidateTechnologyRepository.findActiveTechnologyNamesByCandidateIds(candidateIds)) {
                skillsByCandidate.computeIfAbsent((Long) row[0], k -> new ArrayList<>()).add((String) row[1]);
            }
            for (Object[] row : candidateDomainRepository.findDomainNamesByCandidateIds(candidateIds)) {
                domainsByCandidate.computeIfAbsent((Long) row[0], k -> new ArrayList<>()).add((String) row[1]);
            }
            for (Object[] row : candidateDocumentRepository.findCvDocumentIdsByCandidateIds(candidateIds)) {
                cvByCandidate.putIfAbsent((Long) row[0], (Long) row[1]); // newest first
            }
        }
        Map<Long, List<String>> panelMemberNames = new HashMap<>();
        if (!panelIds.isEmpty()) {
            for (Object[] row : interviewRequestRepository.findActivePanelMemberNames(panelIds)) {
                panelMemberNames.computeIfAbsent((Long) row[0], k -> new ArrayList<>()).add(row[1] + " " + row[2]);
            }
        }

        List<UpcomingWithPrepItem> items = new ArrayList<>();
        for (InterviewSchedule s : pageRows) {
            InterviewRequest req = s.getRequest();
            Candidate c = req != null ? req.getCandidate() : null;
            Long candidateId = c != null ? c.getId() : null;
            Long panelId = req != null && req.getPanel() != null ? req.getPanel().getId() : null;
            boolean isPanel = panelId != null;

            List<String> interviewerNames = new ArrayList<>();
            List<String> panelMembers = new ArrayList<>();
            if (isPanel) {
                interviewerNames.addAll(panelMemberNames.getOrDefault(panelId, List.of()));
                panelMembers.addAll(panelMemberNames.getOrDefault(panelId, List.of()));
            } else if (s.getInterviewer() != null) {
                interviewerNames.add(s.getInterviewer().getFullName());
            }

            List<String> skills = candidateId != null ? skillsByCandidate.getOrDefault(candidateId, List.of()) : List.of();
            List<String> domains = candidateId != null ? domainsByCandidate.getOrDefault(candidateId, List.of()) : List.of();
            Long cvDocId = candidateId != null ? cvByCandidate.get(candidateId) : null;

            items.add(new UpcomingWithPrepItem(
                    s.getId(),
                    req != null ? req.getId() : null,
                    panelId,
                    candidateId,
                    resolveCandidateName(s),
                    TimeZoneMapper.fromUtc(s.getStartDateTime(), zone),
                    TimeZoneMapper.fromUtc(s.getEndDateTime(), zone),
                    s.getInterviewType(),
                    interviewerNames,
                    s.getMeetingLink(),
                    isPanel,
                    false,
                    skills,
                    domains,
                    myCore,
                    myCanDo,
                    panelMembers,
                    cvDocId
            ));
        }
        return CountedList.fromPage(items, schedules.size(), page, size);
    }

    private CalendarHealth buildCalendarHealth(Long interviewerId, ZoneId zone, LocalDateTime now) {
        return calendarCredentialsRepository.findByUserId(interviewerId)
                .map(creds -> {
                    int selectedCount = parseSelectedCalendarCount(creds.getSelectedCalendarIds());
                    var grantExpiresAt = creds.getAuthorizedAt() != null
                            ? creds.getAuthorizedAt().plusDays(7)
                            : creds.getTokenExpiresAt();
                    boolean soon = grantExpiresAt != null
                            && !grantExpiresAt.isAfter(now.plusDays(2));
                    return new CalendarHealth(
                            true,
                            creds.getGoogleAccountEmail(),
                            TimeZoneMapper.fromUtc(creds.getUpdatedAt(), zone),
                            TimeZoneMapper.fromUtc(grantExpiresAt, zone),
                            soon,
                            selectedCount,
                            creds.getAppCalendarId()
                    );
                })
                .orElse(new CalendarHealth(false, null, null, null, false, 0, null));
    }

    private int parseSelectedCalendarCount(String json) {
        if (json == null || json.isBlank()) return 0;
        try {
            String trimmed = json.trim();
            if ("[]".equals(trimmed)) return 0;
            // rough count of quoted ids
            int count = 0;
            int idx = 0;
            while ((idx = trimmed.indexOf('"', idx)) >= 0) {
                int end = trimmed.indexOf('"', idx + 1);
                if (end < 0) break;
                count++;
                idx = end + 1;
            }
            return count / 1; // each id is one quoted string
        } catch (Exception e) {
            return 0;
        }
    }

    private AvailabilityCoverage buildAvailabilityCoverage(
            Long interviewerId, ZoneId zone, LocalDateTime now, int coverageDays) {
        LocalDateTime from = now;
        LocalDateTime to = DashboardPaging.upcomingWindowEnd(now, coverageDays, zone);
        long myOpen = availabilitySlotRepository.countOpenSlotsBetween(interviewerId, from, to);
        long myBooked = availabilitySlotRepository.countBookedSlotsBetween(interviewerId, from, to);
        long totalOpen = availabilitySlotRepository.countAllOpenSlotsBetween(from, to);
        long interviewerCount = Math.max(1, userRepository.countByRoleAndActive(Role.INTERVIEWER, true));
        double teamAvg = (double) totalOpen / interviewerCount;
        LocalDateTime lastEnd = availabilitySlotRepository
                .findLastPublishedAvailableSlotEnd(interviewerId).orElse(null);
        String nudge = null;
        if (myOpen == 0) {
            nudge = "You have no open availability in the next " + coverageDays
                    + " days (including weekends). Consider publishing slots.";
        } else if (myOpen < teamAvg * 0.5) {
            nudge = "Your open availability is below the team average.";
        }
        return new AvailabilityCoverage(
                myOpen,
                myBooked,
                Math.round(teamAvg * 100.0) / 100.0,
                TimeZoneMapper.fromUtc(lastEnd, zone),
                nudge,
                coverageDays
        );
    }

    private Trends buildTrends(Long interviewerId, LocalDateTime now, ZoneId zone) {
        // "This month" / "this quarter" follow the viewer's calendar, converted to UTC for queries.
        LocalDate localToday = DashboardClock.localDate(now, zone);
        LocalDateTime monthFrom = DashboardClock.startOfLocalDateUtc(localToday.withDayOfMonth(1), zone);
        int quarterStartMonth = ((localToday.getMonthValue() - 1) / 3) * 3 + 1;
        LocalDateTime quarterFrom = DashboardClock.startOfLocalDateUtc(
                LocalDate.of(localToday.getYear(), quarterStartMonth, 1), zone);

        long thisMonth = interviewScheduleRepository.countCompletedByInterviewerBetween(
                interviewerId, monthFrom, now.plusDays(1));
        long thisQuarter = interviewScheduleRepository.countCompletedByInterviewerBetween(
                interviewerId, quarterFrom, now.plusDays(1));
        List<TypeCount> byType = interviewScheduleRepository
                .countCompletedByTypeForInterviewer(interviewerId, quarterFrom).stream()
                .map(row -> new TypeCount(
                        row[0] != null ? (String) row[0] : "UNKNOWN",
                        ((Number) row[1]).longValue()))
                .toList();

        // Medians are computed by the database; only my own answers are loaded (for the recommendation mix).
        Double myMedian = round2(feedbackResponseRepository.medianTurnaroundHoursForInterviewerSince(
                interviewerId, quarterFrom));
        Double teamMedian = round2(feedbackResponseRepository.medianTurnaroundHoursSince(quarterFrom));

        List<RecommendationItem> recommendations = parseRecommendations(
                feedbackResponseRepository.findResponsesJsonByInterviewerSince(interviewerId, quarterFrom));
        // Same window as the other trend figures: this quarter.
        long reschedules = postponeRequestRepository
                .countByRequestedByIdAndCreatedAtGreaterThanEqual(interviewerId, quarterFrom);

        return new Trends(
                new InterviewsConducted(thisMonth, thisQuarter, byType),
                new FeedbackTurnaround(myMedian, teamMedian),
                recommendations,
                reschedules
        );
    }

    private Double round2(Double value) {
        return value == null ? null : Math.round(value * 100.0) / 100.0;
    }

    private List<RecommendationItem> parseRecommendations(List<JsonNode> responses) {
        Map<String, Long> counts = new LinkedHashMap<>();
        String[] keys = {"recommendation", "overallRecommendation", "hireRecommendation"};
        for (JsonNode answers : responses) {
            JsonNode json = answers;
            if (json == null || !json.isObject()) continue;
            String value = null;
            for (String key : keys) {
                JsonNode node = json.get(key);
                if (node != null && !node.isNull()) {
                    value = node.isTextual() ? node.asText() : node.toString();
                    break;
                }
                // also scan nested
                Iterator<Map.Entry<String, JsonNode>> fields = json.fields();
                while (fields.hasNext()) {
                    Map.Entry<String, JsonNode> e = fields.next();
                    if (e.getKey() != null && e.getKey().toLowerCase(Locale.ROOT).contains("recommend")) {
                        JsonNode n = e.getValue();
                        if (n != null && !n.isNull()) {
                            value = n.isTextual() ? n.asText() : n.toString();
                            break;
                        }
                    }
                }
                if (value != null) break;
            }
            if (value != null && !value.isBlank()) {
                counts.merge(value.trim(), 1L, Long::sum);
            }
        }
        if (counts.isEmpty()) return List.of();
        return counts.entrySet().stream()
                .map(e -> new RecommendationItem(e.getKey(), e.getValue()))
                .toList();
    }

    private String resolveCandidateName(InterviewSchedule s) {
        if (s.getRequest() == null) return null;
        if (s.getRequest().getCandidate() != null) return s.getRequest().getCandidate().getName();
        return s.getRequest().getCandidateName();
    }

    private double hoursBetween(LocalDateTime start, LocalDateTime end) {
        if (start == null || end == null) return 0;
        return Duration.between(start, end).toMinutes() / 60.0;
    }
}
