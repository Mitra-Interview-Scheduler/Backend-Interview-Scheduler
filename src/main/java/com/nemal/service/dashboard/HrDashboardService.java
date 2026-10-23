package com.nemal.service.dashboard;

import com.nemal.dto.InterviewerMatchRequestDto;
import com.nemal.dto.InterviewerMatchResponseDto;
import com.nemal.dto.dashboard.CountedList;
import com.nemal.dto.dashboard.DashboardActionResponse;
import com.nemal.dto.dashboard.DashboardItemsResponse;
import com.nemal.dto.dashboard.DashboardPaging;
import com.nemal.dto.dashboard.HrDashboardResponse;
import com.nemal.dto.dashboard.HrDashboardResponse.*;
import com.nemal.entity.*;
import com.nemal.enums.AssessmentPhase;
import com.nemal.enums.InterviewStatus;
import com.nemal.enums.MasterStatus;
import com.nemal.enums.PostponeRequestStatus;
import com.nemal.enums.Role;
import com.nemal.enums.SlotStatus;
import com.nemal.repository.*;
import com.nemal.service.HRAvailabilityService;
import com.nemal.service.InterviewTypeService;
import com.nemal.service.NotificationService;
import com.nemal.util.DashboardClock;
import com.nemal.util.TimeZoneMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class HrDashboardService {

    private static final Logger log = LoggerFactory.getLogger(HrDashboardService.class);
    private static final Set<String> SCHEDULING_STATUS_KEYS = Set.of(
            MasterStatus.INTERVIEW_SCHEDULES.name(),
            MasterStatus.TECHNICAL_ROUND.name(),
            MasterStatus.HR_ROUND.name(),
            MasterStatus.DISPOSITION.name()
    );

    private final InterviewPostponeRequestRepository postponeRequestRepository;
    private final InterviewScheduleRepository interviewScheduleRepository;
    private final FeedbackResponseRepository feedbackResponseRepository;
    private final CandidateRepository candidateRepository;
    private final CandidatePipelineStatusEventRepository pipelineEventRepository;
    private final CandidateScreeningRepository candidateScreeningRepository;
    private final AssessmentReviewerRepository assessmentReviewerRepository;
    private final AvailabilitySlotRepository availabilitySlotRepository;
    private final CandidateClosureRepository candidateClosureRepository;
    private final MasterStepRepository masterStepRepository;
    private final NotificationService notificationService;
    private final HRAvailabilityService hrAvailabilityService;
    private final FeedbackNudgeLogRepository feedbackNudgeLogRepository;
    private final InterviewTypeService interviewTypeService;

    public HrDashboardService(
            InterviewPostponeRequestRepository postponeRequestRepository,
            InterviewScheduleRepository interviewScheduleRepository,
            FeedbackResponseRepository feedbackResponseRepository,
            CandidateRepository candidateRepository,
            CandidatePipelineStatusEventRepository pipelineEventRepository,
            CandidateScreeningRepository candidateScreeningRepository,
            AssessmentReviewerRepository assessmentReviewerRepository,
            AvailabilitySlotRepository availabilitySlotRepository,
            CandidateClosureRepository candidateClosureRepository,
            MasterStepRepository masterStepRepository,
            NotificationService notificationService,
            HRAvailabilityService hrAvailabilityService,
            FeedbackNudgeLogRepository feedbackNudgeLogRepository,
            InterviewTypeService interviewTypeService
    ) {
        this.postponeRequestRepository = postponeRequestRepository;
        this.interviewScheduleRepository = interviewScheduleRepository;
        this.feedbackResponseRepository = feedbackResponseRepository;
        this.candidateRepository = candidateRepository;
        this.pipelineEventRepository = pipelineEventRepository;
        this.candidateScreeningRepository = candidateScreeningRepository;
        this.assessmentReviewerRepository = assessmentReviewerRepository;
        this.availabilitySlotRepository = availabilitySlotRepository;
        this.candidateClosureRepository = candidateClosureRepository;
        this.masterStepRepository = masterStepRepository;
        this.notificationService = notificationService;
        this.hrAvailabilityService = hrAvailabilityService;
        this.feedbackNudgeLogRepository = feedbackNudgeLogRepository;
        this.interviewTypeService = interviewTypeService;
    }

    @Transactional(readOnly = true)
    public HrDashboardResponse getDashboard(
            User user,
            boolean mineOnly,
            int stalledDays,
            int feedbackOverdueHours,
            int capacityDays,
            int trendDays,
            int previewSize,
            int upcomingDays,
            ZoneId zone
    ) {
        Long hrId = mineOnly && user != null ? user.getId() : null;
        LocalDateTime now = DashboardClock.nowUtc();
        int preview = DashboardPaging.previewSize(previewSize);
        int upcomingWindow = DashboardPaging.upcomingDays(upcomingDays);
        stalledDays = DashboardPaging.stalledDays(stalledDays);
        feedbackOverdueHours = DashboardPaging.feedbackOverdueHours(feedbackOverdueHours);
        capacityDays = DashboardPaging.capacityDays(capacityDays);
        trendDays = DashboardPaging.trendDays(trendDays);

        ActionQueue actionQueue = new ActionQueue(
                buildPostponeRequests(hrId, zone, now, 0, preview),
                buildFeedbackOverdue(hrId, feedbackOverdueHours, zone, now, 0, preview),
                buildStalledCandidates(hrId, stalledDays, zone, now, 0, preview),
                buildAssessmentsWaiting(hrId, zone, now, 0, preview),
                buildOffersPending(hrId, zone, now, 0, preview),
                buildScreeningBacklog(hrId, zone, now, 0, preview)
        );

        LiveStatus liveStatus = new LiveStatus(
                buildUpcomingInterviews(hrId, zone, now, upcomingWindow, 0, preview),
                buildPipelineFunnel(hrId),
                buildInterviewerCapacity(capacityDays, now, zone),
                List.of()
        );

        Trends trends = buildTrends(trendDays, now);

        return new HrDashboardResponse(actionQueue, liveStatus, trends, TimeZoneMapper.fromUtc(now, zone));
    }

    @Transactional(readOnly = true)
    public DashboardItemsResponse getDashboardItems(
            User user,
            String section,
            Integer pageRaw,
            Integer sizeRaw,
            boolean mineOnly,
            int stalledDays,
            int feedbackOverdueHours,
            int upcomingDays,
            ZoneId zone
    ) {
        int page = DashboardPaging.page(pageRaw);
        int size = DashboardPaging.size(sizeRaw);
        int upcomingWindow = DashboardPaging.upcomingDays(upcomingDays);
        stalledDays = DashboardPaging.stalledDays(stalledDays);
        feedbackOverdueHours = DashboardPaging.feedbackOverdueHours(feedbackOverdueHours);
        Long hrId = mineOnly && user != null ? user.getId() : null;
        LocalDateTime now = DashboardClock.nowUtc();
        String key = section == null ? "" : section.trim();

        return switch (key) {
            case "postponeRequests" ->
                    DashboardItemsResponse.of(key, buildPostponeRequests(hrId, zone, now, page, size));
            case "feedbackOverdue" ->
                    DashboardItemsResponse.of(key, buildFeedbackOverdue(hrId, feedbackOverdueHours, zone, now, page, size));
            case "candidatesStalled" ->
                    DashboardItemsResponse.of(key, buildStalledCandidates(hrId, stalledDays, zone, now, page, size));
            case "offersPending" ->
                    DashboardItemsResponse.of(key, buildOffersPending(hrId, zone, now, page, size));
            case "screeningBacklog" ->
                    DashboardItemsResponse.of(key, buildScreeningBacklog(hrId, zone, now, page, size));
            case "awaitingReceived" ->
                    DashboardItemsResponse.of(key, buildAssessmentsWaiting(hrId, zone, now, page, size).awaitingReceived());
            case "awaitingReviewer" ->
                    DashboardItemsResponse.of(key, buildAssessmentsWaiting(hrId, zone, now, page, size).awaitingReviewer());
            case "reviewIncomplete" ->
                    DashboardItemsResponse.of(key, buildAssessmentsWaiting(hrId, zone, now, page, size).reviewIncomplete());
            case "upcomingInterviews" ->
                    DashboardItemsResponse.of(key, buildUpcomingInterviews(hrId, zone, now, upcomingWindow, page, size));
            default -> throw new IllegalArgumentException("Unknown dashboard section");
        };
    }

    /** Minimum time between two reminders to the same interviewer for the same interview. */
    static final Duration NUDGE_COOLDOWN = Duration.ofHours(4);

    @Transactional
    public DashboardActionResponse nudgeFeedbackOverdue(User nudgedBy, Long scheduleId) {
        if (nudgedBy == null) {
            throw new IllegalArgumentException("Authenticated user required to send ping");
        }
        InterviewSchedule schedule = interviewScheduleRepository.findByIdWithPostponeDetails(scheduleId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Interview not found"));

        // HR (non-admin) only works with the candidates they coordinate, same as the dashboard lists.
        if (!nudgedBy.hasRole(Role.ADMIN) && !matchesHrSchedule(schedule, nudgedBy.getId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "You can only send reminders for candidates you coordinate");
        }

        LocalDateTime now = DashboardClock.nowUtc();
        LocalDateTime cutoff = now.minusHours(DashboardPaging.DEFAULT_FEEDBACK_OVERDUE_HOURS);
        if (!isFeedbackOverdue(schedule, cutoff)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "This interview is not overdue for feedback");
        }

        // Each recipient is paired with their own schedule: panel members each have one.
        Map<User, InterviewSchedule> candidates = new LinkedHashMap<>();
        InterviewRequest request = schedule.getRequest();
        if (request != null && request.getPanel() != null) {
            for (InterviewSchedule panelSchedule : interviewScheduleRepository.findByPanelId(request.getPanel().getId())) {
                if (panelSchedule.getInterviewer() != null
                        && panelSchedule.getStatus() != InterviewStatus.CANCELLED
                        && panelSchedule.getAssessmentPhase() == null) {
                    candidates.putIfAbsent(panelSchedule.getInterviewer(), panelSchedule);
                }
            }
        } else if (schedule.getInterviewer() != null) {
            candidates.put(schedule.getInterviewer(), schedule);
        } else if (request != null && request.getAssignedInterviewer() != null) {
            candidates.put(request.getAssignedInterviewer(), schedule);
        }
        if (candidates.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "No interviewer is assigned to this interview");
        }

        // Skip anyone who already submitted feedback for their schedule.
        Set<Long> submitted = new HashSet<>(feedbackResponseRepository.findScheduleIdsWithFeedback(
                candidates.values().stream().map(InterviewSchedule::getId).toList()));
        candidates.entrySet().removeIf(e -> submitted.contains(e.getValue().getId()));
        if (candidates.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Feedback has already been submitted");
        }

        String candidateName = resolveCandidateName(schedule);
        int sent = 0;
        LocalDateTime lastReminder = null;
        for (Map.Entry<User, InterviewSchedule> entry : candidates.entrySet()) {
            User interviewer = entry.getKey();
            InterviewSchedule own = entry.getValue();
            Optional<FeedbackNudgeLog> previous = feedbackNudgeLogRepository
                    .findTopByInterviewScheduleIdAndRecipientIdOrderByCreatedAtDesc(own.getId(), interviewer.getId());
            if (previous.isPresent() && previous.get().getCreatedAt() != null
                    && previous.get().getCreatedAt().isAfter(now.minus(NUDGE_COOLDOWN))) {
                if (lastReminder == null || previous.get().getCreatedAt().isAfter(lastReminder)) {
                    lastReminder = previous.get().getCreatedAt();
                }
                continue;
            }
            notificationService.sendFeedbackNudgeNotification(interviewer, candidateName, own.getId());
            feedbackNudgeLogRepository.save(FeedbackNudgeLog.builder()
                    .interviewSchedule(own)
                    .nudgedBy(nudgedBy)
                    .recipient(interviewer)
                    .build());
            sent++;
        }
        if (sent == 0) {
            long minutesAgo = lastReminder != null ? Math.max(1, Duration.between(lastReminder, now).toMinutes()) : 0;
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Already reminded " + formatAgo(minutesAgo) + ". You can remind again after "
                            + NUDGE_COOLDOWN.toHours() + " hours.");
        }
        return DashboardActionResponse.ok("Reminder sent to " + sent + " interviewer(s)");
    }

    /** Same rule as the overdue list: not cancelled, not an assessment, ended before the cutoff. */
    private boolean isFeedbackOverdue(InterviewSchedule s, LocalDateTime cutoff) {
        if (s.getAssessmentPhase() != null) return false;
        if (s.getStatus() != InterviewStatus.COMPLETED && s.getStatus() != InterviewStatus.SCHEDULED) return false;
        LocalDateTime ended = s.getCompletedAt() != null ? s.getCompletedAt() : s.getEndDateTime();
        return ended != null && ended.isBefore(cutoff);
    }

    private String formatAgo(long minutes) {
        if (minutes < 60) return minutes + " minute(s) ago";
        return (minutes / 60) + " hour(s) ago";
    }

    private CountedList<PostponeItem> buildPostponeRequests(
            Long hrId, ZoneId zone, LocalDateTime now, int page, int size) {
        List<InterviewPostponeRequest> filtered = postponeRequestRepository
                .findPendingWithDetails(PostponeRequestStatus.PENDING)
                .stream()
                .filter(p -> matchesHr(p, hrId))
                .toList();
        List<PostponeItem> items = DashboardPaging.slice(filtered, page, size).stream()
                .map(p -> toPostponeItem(p, zone, now))
                .toList();
        return CountedList.fromPage(items, filtered.size(), page, size);
    }

    private boolean matchesHr(InterviewPostponeRequest p, Long hrId) {
        if (hrId == null) return true;
        Candidate c = resolveCandidate(p);
        return c != null && c.getCoordinatedHr() != null && hrId.equals(c.getCoordinatedHr().getId());
    }

    private PostponeItem toPostponeItem(InterviewPostponeRequest p, ZoneId zone, LocalDateTime now) {
        InterviewSchedule sch = p.getInterviewSchedule();
        InterviewRequest req = p.getInterviewRequest() != null ? p.getInterviewRequest()
                : (sch != null ? sch.getRequest() : null);
        Candidate cand = resolveCandidate(p);
        User interviewer = sch != null && sch.getInterviewer() != null
                ? sch.getInterviewer()
                : (req != null ? req.getAssignedInterviewer() : null);
        LocalDateTime created = p.getCreatedAt() != null ? p.getCreatedAt() : now;
        double ageHours = hoursBetween(created, now);
        Long panelId = req != null && req.getPanel() != null ? req.getPanel().getId() : null;
        return new PostponeItem(
                p.getId(),
                sch != null ? sch.getId() : null,
                cand != null ? cand.getId() : null,
                cand != null ? cand.getName() : (req != null ? req.getCandidateName() : null),
                interviewer != null ? interviewer.getFullName() : null,
                tz(sch != null ? sch.getStartDateTime() : (req != null ? req.getPreferredStartDateTime() : null), zone),
                tz(sch != null ? sch.getEndDateTime() : (req != null ? req.getPreferredEndDateTime() : null), zone),
                tz(p.getPreferredStartDateTime(), zone),
                tz(p.getPreferredEndDateTime(), zone),
                p.getReason(),
                tz(created, zone),
                ageHours,
                panelId
        );
    }

    private CountedList<FeedbackOverdueItem> buildFeedbackOverdue(
            Long hrId, int feedbackOverdueHours, ZoneId zone, LocalDateTime now, int page, int size) {
        LocalDateTime cutoff = now.minusHours(Math.max(1, feedbackOverdueHours));
        List<InterviewSchedule> overdueAll = interviewScheduleRepository.findFeedbackOverdue(cutoff).stream()
                .sorted(Comparator.comparing(
                        (InterviewSchedule s) -> s.getCompletedAt() != null ? s.getCompletedAt() : s.getEndDateTime(),
                        Comparator.nullsLast(Comparator.naturalOrder())))
                .filter(s -> matchesHrSchedule(s, hrId))
                .toList();
        List<InterviewSchedule> overdue = DashboardPaging.slice(overdueAll, page, size);

        List<Long> scheduleIds = overdue.stream().map(InterviewSchedule::getId).toList();
        Map<Long, Long> nudgeCounts = new HashMap<>();
        Map<Long, FeedbackNudgeLog> latestNudges = new HashMap<>();
        if (!scheduleIds.isEmpty()) {
            for (Object[] row : feedbackNudgeLogRepository.countByScheduleIds(scheduleIds)) {
                if (row[0] != null && row[1] != null) {
                    nudgeCounts.put((Long) row[0], (Long) row[1]);
                }
            }
            for (FeedbackNudgeLog log : feedbackNudgeLogRepository.findLatestByScheduleIds(scheduleIds)) {
                if (log.getInterviewSchedule() != null) {
                    latestNudges.put(log.getInterviewSchedule().getId(), log);
                }
            }
        }

        List<FeedbackOverdueItem> items = overdue.stream()
                .map(s -> {
                    LocalDateTime completedOrEnd = s.getCompletedAt() != null ? s.getCompletedAt() : s.getEndDateTime();
                    Candidate c = s.getRequest() != null ? s.getRequest().getCandidate() : null;
                    User interviewer = s.getInterviewer();
                    FeedbackNudgeLog latest = latestNudges.get(s.getId());
                    return new FeedbackOverdueItem(
                            s.getId(),
                            s.getRequest() != null ? s.getRequest().getId() : null,
                            c != null ? c.getId() : null,
                            resolveCandidateName(s),
                            interviewer != null ? interviewer.getId() : null,
                            interviewer != null ? interviewer.getFullName() : null,
                            s.getInterviewType(),
                            tz(completedOrEnd, zone),
                            hoursBetween(completedOrEnd != null ? completedOrEnd : now, now),
                            s.getMeetingLink(),
                            nudgeCounts.getOrDefault(s.getId(), 0L),
                            latest != null ? tz(latest.getCreatedAt(), zone) : null,
                            latest != null && latest.getNudgedBy() != null
                                    ? latest.getNudgedBy().getFullName() : null
                    );
                })
                .toList();
        return CountedList.fromPage(items, overdueAll.size(), page, size);
    }

    private CountedList<StalledCandidateItem> buildStalledCandidates(
            Long hrId, int stalledDays, ZoneId zone, LocalDateTime now, int page, int size) {
        List<Candidate> active = candidateRepository.findActiveNonClosed(hrId);
        if (active.isEmpty()) {
            return CountedList.empty();
        }
        List<Long> ids = active.stream().map(Candidate::getId).toList();
        Map<Long, LocalDateTime> latest = new HashMap<>();
        for (Object[] row : pipelineEventRepository.findLatestEventAtByCandidateIds(ids)) {
            if (row[0] != null && row[1] != null) {
                latest.put((Long) row[0], (LocalDateTime) row[1]);
            }
        }
        int threshold = Math.max(1, stalledDays);
        List<AbstractMap.SimpleEntry<Candidate, AbstractMap.SimpleEntry<LocalDateTime, Long>>> matched = active.stream()
                .map(c -> {
                    LocalDateTime lastChange = latest.getOrDefault(c.getId(),
                            c.getUpdatedAt() != null ? c.getUpdatedAt() : c.getCreatedAt());
                    if (lastChange == null) lastChange = now;
                    long days = DashboardClock.localDaysBetween(lastChange, now, zone);
                    return new AbstractMap.SimpleEntry<>(c, new AbstractMap.SimpleEntry<>(lastChange, days));
                })
                .filter(e -> e.getValue().getValue() >= threshold)
                .sorted((a, b) -> Long.compare(b.getValue().getValue(), a.getValue().getValue()))
                .toList();
        List<StalledCandidateItem> items = DashboardPaging.slice(matched, page, size)
                .stream()
                .map(e -> {
                    Candidate c = e.getKey();
                    LocalDateTime lastChange = e.getValue().getKey();
                    long days = e.getValue().getValue();
                    MasterStep step = c.getMasterStep();
                    return new StalledCandidateItem(
                            c.getId(),
                            c.getName(),
                            c.getStatusKey(),
                            step != null ? step.getLabel() : c.getStatusKey(),
                            c.getDepartment() != null ? c.getDepartment().getName() : null,
                            c.getCoordinatedHr() != null ? c.getCoordinatedHr().getFullName() : null,
                            tz(lastChange, zone),
                            days
                    );
                })
                .toList();
        return CountedList.fromPage(items, matched.size(), page, size);
    }

    private AssessmentsWaiting buildAssessmentsWaiting(Long hrId, ZoneId zone, LocalDateTime now, int page, int size) {
        List<InterviewSchedule> assessments = interviewScheduleRepository.findByAssessmentPhases(
                List.of(AssessmentPhase.AWAITING, AssessmentPhase.RECEIVED, AssessmentPhase.UNDER_REVIEW));
        List<AssessmentWaitingItem> awaiting = new ArrayList<>();
        List<AssessmentWaitingItem> awaitingReviewer = new ArrayList<>();
        List<AssessmentWaitingItem> incomplete = new ArrayList<>();

        for (InterviewSchedule s : assessments) {
            if (!matchesHrSchedule(s, hrId)) continue;
            List<AssessmentReviewer> reviewers = assessmentReviewerRepository
                    .findByInterviewScheduleIdOrderByAssignedAtAsc(s.getId());
            AssessmentWaitingItem item = toAssessmentItem(s, reviewers.size(), zone, now);
            if (s.getAssessmentPhase() == AssessmentPhase.AWAITING) {
                awaiting.add(item);
            } else if (s.getAssessmentPhase() == AssessmentPhase.RECEIVED && reviewers.isEmpty()) {
                awaitingReviewer.add(item);
            } else if (s.getAssessmentPhase() == AssessmentPhase.UNDER_REVIEW
                    || (s.getAssessmentPhase() == AssessmentPhase.RECEIVED && !reviewers.isEmpty())) {
                boolean anyMissing = reviewers.stream().anyMatch(r ->
                        !feedbackResponseRepository.existsByInterviewScheduleIdAndInterviewerId(
                                s.getId(), r.getReviewer().getId()));
                if (anyMissing || reviewers.isEmpty()) {
                    incomplete.add(item);
                }
            }
        }
        return new AssessmentsWaiting(
                CountedList.page(awaiting, page, size),
                CountedList.page(awaitingReviewer, page, size),
                CountedList.page(incomplete, page, size)
        );
    }

    private AssessmentWaitingItem toAssessmentItem(
            InterviewSchedule s, int reviewerCount, ZoneId zone, LocalDateTime now) {
        Candidate c = s.getRequest() != null ? s.getRequest().getCandidate() : null;
        LocalDateTime due = s.getEndDateTime() != null ? s.getEndDateTime() : s.getStartDateTime();
        long aging = due != null ? Math.max(0, DashboardClock.localDaysBetween(due, now, zone)) : 0;
        String nextAction;
        if (s.getAssessmentPhase() == AssessmentPhase.AWAITING) {
            nextAction = "Mark as received";
        } else if (s.getAssessmentPhase() == AssessmentPhase.RECEIVED && reviewerCount == 0) {
            nextAction = "Assign reviewers";
        } else {
            nextAction = "Follow up on reviews";
        }
        String typeCode = s.getInterviewType();
        String assessmentName = typeCode != null && !typeCode.isBlank()
                ? interviewTypeService.labelForCode(typeCode)
                : "Assessment";
        return new AssessmentWaitingItem(
                s.getId(),
                c != null ? c.getId() : null,
                resolveCandidateName(s),
                assessmentName,
                s.getAssessmentPhase() != null ? s.getAssessmentPhase().name() : null,
                aging,
                tz(due, zone),
                reviewerCount,
                nextAction,
                c != null && c.getDepartment() != null ? c.getDepartment().getName() : null
        );
    }

    private CountedList<OfferPendingItem> buildOffersPending(
            Long hrId, ZoneId zone, LocalDateTime now, int page, int size) {
        List<Candidate> offers = candidateRepository.findActiveByStatusKey(
                MasterStatus.OFFER_PENDING.name(), hrId);
        Map<Long, LocalDateTime> latest = latestEvents(offers);
        List<OfferPendingItem> items = DashboardPaging.slice(offers, page, size).stream()
                .map(c -> {
                    LocalDateTime entered = latest.getOrDefault(c.getId(),
                            c.getUpdatedAt() != null ? c.getUpdatedAt() : c.getCreatedAt());
                    long days = entered != null
                            ? DashboardClock.localDaysBetween(entered, now, zone) : 0;
                    return new OfferPendingItem(
                            c.getId(),
                            c.getName(),
                            c.getStatusKey(),
                            c.getDepartment() != null ? c.getDepartment().getName() : null,
                            days,
                            c.getCoordinatedHr() != null ? c.getCoordinatedHr().getFullName() : null
                    );
                })
                .toList();
        return CountedList.fromPage(items, offers.size(), page, size);
    }

    private CountedList<ScreeningBacklogItem> buildScreeningBacklog(
            Long hrId, ZoneId zone, LocalDateTime now, int page, int size) {
        List<Candidate> backlog = candidateScreeningRepository.findCandidatesNeedingScreening(
                List.of(MasterStatus.NEW.name(), MasterStatus.SCREENING.name()), hrId);
        List<ScreeningBacklogItem> items = DashboardPaging.slice(backlog, page, size).stream()
                .map(c -> {
                    LocalDateTime applied = c.getAppliedAt() != null ? c.getAppliedAt() : c.getCreatedAt();
                    long days = applied != null
                            ? DashboardClock.localDaysBetween(applied, now, zone) : 0;
                    return new ScreeningBacklogItem(
                            c.getId(),
                            c.getName(),
                            c.getStatusKey(),
                            tz(applied, zone),
                            days,
                            c.getCoordinatedHr() != null ? c.getCoordinatedHr().getFullName() : null
                    );
                })
                .toList();
        return CountedList.fromPage(items, backlog.size(), page, size);
    }

    private CountedList<UpcomingInterviewItem> buildUpcomingInterviews(
            Long hrId, ZoneId zone, LocalDateTime now, int upcomingDays, int page, int size) {
        LocalDateTime from = DashboardClock.startOfLocalDayUtc(now, zone, 0);
        LocalDateTime to = DashboardPaging.upcomingWindowEnd(now, upcomingDays, zone);
        List<InterviewSchedule> all = interviewScheduleRepository.findUpcomingInRange(from, to).stream()
                .filter(s -> matchesHrSchedule(s, hrId))
                .filter(s -> s.getStatus() == InterviewStatus.SCHEDULED)
                .toList();
        List<UpcomingInterviewItem> items = DashboardPaging.slice(all, page, size).stream()
                .map(s -> toUpcoming(s, zone))
                .toList();
        return CountedList.fromPage(items, all.size(), page, size);
    }

    private UpcomingInterviewItem toUpcoming(InterviewSchedule s, ZoneId zone) {
        InterviewRequest req = s.getRequest();
        Candidate c = req != null ? req.getCandidate() : null;
        Long panelId = req != null && req.getPanel() != null ? req.getPanel().getId() : null;
        boolean isPanel = panelId != null;
        List<String> names = new ArrayList<>();
        if (isPanel && req.getPanel().getPanelRequests() != null) {
            for (InterviewRequest pr : req.getPanel().getPanelRequests()) {
                if (pr.getAssignedInterviewer() != null) {
                    names.add(pr.getAssignedInterviewer().getFullName());
                }
            }
        } else if (s.getInterviewer() != null) {
            names.add(s.getInterviewer().getFullName());
        }
        return new UpcomingInterviewItem(
                s.getId(),
                req != null ? req.getId() : null,
                panelId,
                c != null ? c.getId() : null,
                resolveCandidateName(s),
                tz(s.getStartDateTime(), zone),
                tz(s.getEndDateTime(), zone),
                s.getInterviewType(),
                names,
                s.getMeetingLink(),
                isPanel,
                false
        );
    }

    private List<PipelineFunnelItem> buildPipelineFunnel(Long hrId) {
        return candidateRepository.countPipelineFunnel(hrId).stream()
                .map(row -> new PipelineFunnelItem(
                        (String) row[0],
                        (String) row[1],
                        ((Number) row[3]).longValue(),
                        row[2] != null ? ((Number) row[2]).intValue() : 0
                ))
                .toList();
    }

    private List<CapacityDay> buildInterviewerCapacity(int capacityDays, LocalDateTime now, ZoneId zone) {
        LocalDateTime from = DashboardClock.startOfLocalDayUtc(now, zone, 0);
        LocalDateTime to = DashboardClock.startOfLocalDayUtc(now, zone, Math.max(1, capacityDays));
        List<AvailabilitySlot> slots = availabilitySlotRepository.findAllActiveSlotsForHRByDateRange(from, to);
        Map<String, CapacityDay> map = new LinkedHashMap<>();
        for (AvailabilitySlot slot : slots) {
            if (slot.getStartDateTime() == null) continue;
            String date = DashboardClock.localDate(slot.getStartDateTime(), zone).toString();
            Department dept = slot.getInterviewer() != null ? slot.getInterviewer().getDepartment() : null;
            Long deptId = dept != null ? dept.getId() : null;
            String deptName = dept != null ? dept.getName() : null;
            String key = date + "|" + (deptId != null ? deptId : "null");
            CapacityDay existing = map.getOrDefault(key,
                    new CapacityDay(date, deptId, deptName, 0, 0));
            long avail = existing.availableSlots();
            long booked = existing.bookedSlots();
            if (slot.getStatus() == SlotStatus.AVAILABLE) avail++;
            else if (slot.getStatus() == SlotStatus.BOOKED) booked++;
            map.put(key, new CapacityDay(date, deptId, deptName, avail, booked));
        }
        return new ArrayList<>(map.values());
    }

    private List<UnfilledNeedItem> buildUnfilledNeeds(Long hrId, LocalDateTime now) {
        List<String> scheduleKeys = new ArrayList<>(SCHEDULING_STATUS_KEYS);
        masterStepRepository.findByIsActiveTrueOrderByStepOrderAscDisplayOrderAsc().stream()
                .filter(ms -> !ms.isClosingStep())
                .filter(ms -> !MasterStatus.NEW.name().equals(ms.getStatusKey()))
                .filter(ms -> !MasterStatus.SCREENING.name().equals(ms.getStatusKey()))
                .filter(ms -> !MasterStatus.OFFER_PENDING.name().equals(ms.getStatusKey()))
                .filter(ms -> !MasterStatus.ON_HOLD.name().equals(ms.getStatusKey()))
                .map(MasterStep::getStatusKey)
                .filter(k -> !scheduleKeys.contains(k))
                .forEach(scheduleKeys::add);

        List<Candidate> candidates = candidateRepository.findActiveByStatusKeys(scheduleKeys, hrId);
        List<UnfilledNeedItem> needs = new ArrayList<>();
        for (Candidate c : candidates) {
            if (needs.size() >= DashboardPaging.MAX_PAGE_SIZE) break;
            try {
                InterviewerMatchResponseDto match = hrAvailabilityService.matchInterviewers(
                        new InterviewerMatchRequestDto(c.getId(), null, null, null, null, null, 5));
                boolean hasMatch = match != null && (
                        (match.both() != null && !match.both().isEmpty())
                                || (match.technologies() != null && !match.technologies().isEmpty())
                                || (match.domains() != null && !match.domains().isEmpty()));
                if (!hasMatch) {
                    needs.add(new UnfilledNeedItem(
                            c.getId(),
                            c.getName(),
                            c.getStatusKey(),
                            c.getDepartment() != null ? c.getDepartment().getName() : null,
                            c.getTargetDesignation() != null ? c.getTargetDesignation().getName() : null,
                            "NO_MATCHING_INTERVIEWER"
                    ));
                    continue;
                }
                Set<Long> matcherIds = new LinkedHashSet<>();
                if (match.both() != null) {
                    match.both().forEach(m -> { if (m != null && m.interviewerId() != null) matcherIds.add(m.interviewerId()); });
                }
                if (match.technologies() != null) {
                    match.technologies().forEach(m -> { if (m != null && m.interviewerId() != null) matcherIds.add(m.interviewerId()); });
                }
                if (match.domains() != null) {
                    match.domains().forEach(m -> { if (m != null && m.interviewerId() != null) matcherIds.add(m.interviewerId()); });
                }
                LocalDateTime to = now.plusDays(14);
                List<Long> withSlots = matcherIds.isEmpty()
                        ? List.of()
                        : availabilitySlotRepository.findInterviewerIdsWithAvailableSlotsBetween(
                                matcherIds, now, to);
                if (withSlots.isEmpty()) {
                    needs.add(new UnfilledNeedItem(
                            c.getId(),
                            c.getName(),
                            c.getStatusKey(),
                            c.getDepartment() != null ? c.getDepartment().getName() : null,
                            c.getTargetDesignation() != null ? c.getTargetDesignation().getName() : null,
                            "NO_FREE_SLOT"
                    ));
                }
            } catch (Exception ex) {
                log.debug("Unfilled need check skipped for candidate {}: {}", c.getId(), ex.getMessage());
            }
        }
        return needs;
    }

    private Trends buildTrends(int trendDays, LocalDateTime now) {
        LocalDateTime from = now.minusDays(Math.max(1, trendDays));
        return new Trends(
                buildTimeInStage(from),
                buildTimeToSchedule(from),
                buildCloseReasons(from),
                buildOfferMetrics(from),
                buildCoordinatorWorkload(from),
                buildRescheduleRate(from, now)
        );
    }

    private List<TimeInStageItem> buildTimeInStage(LocalDateTime from) {
        List<CandidatePipelineStatusEvent> events = pipelineEventRepository.findEventsSince(from);
        // Group durations by previous step (time spent in previous status before leaving)
        Map<String, List<Double>> durations = new HashMap<>();
        Map<String, String> labels = new HashMap<>();
        Map<Long, List<CandidatePipelineStatusEvent>> byCandidate = events.stream()
                .filter(e -> e.getCandidate() != null)
                .collect(Collectors.groupingBy(e -> e.getCandidate().getId()));

        for (List<CandidatePipelineStatusEvent> timeline : byCandidate.values()) {
            timeline.sort(Comparator.comparing(CandidatePipelineStatusEvent::getCreatedAt,
                    Comparator.nullsLast(Comparator.naturalOrder())));
            for (int i = 1; i < timeline.size(); i++) {
                CandidatePipelineStatusEvent prev = timeline.get(i - 1);
                CandidatePipelineStatusEvent curr = timeline.get(i);
                if (prev.getCreatedAt() == null || curr.getCreatedAt() == null) continue;
                MasterStep step = prev.getMasterStep();
                if (step == null) continue;
                double days = ChronoUnit.HOURS.between(prev.getCreatedAt(), curr.getCreatedAt()) / 24.0;
                durations.computeIfAbsent(step.getStatusKey(), k -> new ArrayList<>()).add(days);
                labels.put(step.getStatusKey(), step.getLabel());
            }
        }
        List<TimeInStageItem> result = new ArrayList<>();
        for (Map.Entry<String, List<Double>> e : durations.entrySet()) {
            List<Double> vals = e.getValue().stream().sorted().toList();
            result.add(new TimeInStageItem(
                    e.getKey(),
                    labels.getOrDefault(e.getKey(), e.getKey()),
                    percentile(vals, 0.5),
                    percentile(vals, 0.9),
                    vals.size()
            ));
        }
        result.sort(Comparator.comparing(TimeInStageItem::statusKey));
        return result;
    }

    private TimeToSchedule buildTimeToSchedule(LocalDateTime from) {
        // Approximate: hours from candidate createdAt to first SCHEDULED interview start
        List<Candidate> active = candidateRepository.findActiveNonClosed(null);
        List<Double> overall = new ArrayList<>();
        Map<String, List<Double>> byDept = new HashMap<>();
        Map<String, List<Double>> byTier = new HashMap<>();
        for (Candidate c : active) {
            if (c.getCreatedAt() == null || c.getCreatedAt().isBefore(from)) continue;
            // skip heavy per-candidate queries; use appliedAt -> first event INTERVIEW_SCHEDULES if present
        }
        List<CandidatePipelineStatusEvent> events = pipelineEventRepository.findEventsSince(from);
        Map<Long, LocalDateTime> firstSchedule = new HashMap<>();
        for (CandidatePipelineStatusEvent e : events) {
            if (e.getMasterStep() == null || e.getCandidate() == null) continue;
            if (MasterStatus.INTERVIEW_SCHEDULES.name().equals(e.getMasterStep().getStatusKey())
                    || "INTERVIEW_SCHEDULES".equals(e.getMasterStep().getStatusKey())) {
                firstSchedule.putIfAbsent(e.getCandidate().getId(), e.getCreatedAt());
            }
        }
        Map<Long, Candidate> candMap = active.stream()
                .collect(Collectors.toMap(Candidate::getId, c -> c, (a, b) -> a));
        for (Map.Entry<Long, LocalDateTime> e : firstSchedule.entrySet()) {
            Candidate c = candMap.get(e.getKey());
            if (c == null || c.getCreatedAt() == null || e.getValue() == null) continue;
            double hours = hoursBetween(c.getCreatedAt(), e.getValue());
            if (hours < 0) continue;
            overall.add(hours);
            String dept = c.getDepartment() != null ? c.getDepartment().getName() : "Unknown";
            byDept.computeIfAbsent(dept, k -> new ArrayList<>()).add(hours);
            String tier = c.getTargetDesignation() != null && c.getTargetDesignation().getTier() != null
                    ? c.getTargetDesignation().getTier().getName() : "Unknown";
            byTier.computeIfAbsent(tier, k -> new ArrayList<>()).add(hours);
        }
        return new TimeToSchedule(
                overall.isEmpty() ? null : percentile(overall.stream().sorted().toList(), 0.5),
                toNamedMetrics(byDept),
                toNamedMetrics(byTier)
        );
    }

    private List<NamedMetric> toNamedMetrics(Map<String, List<Double>> map) {
        List<NamedMetric> list = new ArrayList<>();
        for (Map.Entry<String, List<Double>> e : map.entrySet()) {
            List<Double> sorted = e.getValue().stream().sorted().toList();
            list.add(new NamedMetric(e.getKey(), percentile(sorted, 0.5), sorted.size()));
        }
        return list;
    }

    private List<CloseReasonItem> buildCloseReasons(LocalDateTime from) {
        return candidateClosureRepository.countByCloseReasonSince(from).stream()
                .map(row -> new CloseReasonItem(
                        (String) row[0],
                        (String) row[1],
                        ((Number) row[2]).longValue()
                ))
                .toList();
    }

    private OfferMetrics buildOfferMetrics(LocalDateTime from) {
        List<CandidateClosure> closures = candidateClosureRepository.findByClosedAtAfter(from);
        long offersMade = closures.stream()
                .filter(c -> c.getClosedStatusKey() != null
                        && (c.getClosedStatusKey().contains("OFFER")
                        || MasterStatus.SELECTED.name().equals(c.getClosedStatusKey())
                        || MasterStatus.OFFER_PENDING.name().equals(c.getClosedStatusKey())))
                .count();
        // Also count candidates currently/ever in offer via pipeline events
        long offerEvents = pipelineEventRepository.findEventsSince(from).stream()
                .filter(e -> e.getMasterStep() != null
                        && MasterStatus.OFFER_PENDING.name().equals(e.getMasterStep().getStatusKey()))
                .map(e -> e.getCandidate() != null ? e.getCandidate().getId() : null)
                .filter(Objects::nonNull)
                .distinct()
                .count();
        offersMade = Math.max(offersMade, offerEvents);

        long accepted = closures.stream()
                .filter(c -> MasterStatus.SELECTED.name().equals(c.getClosedStatusKey()))
                .count();
        long activeSelected = candidateRepository
                .findActiveByStatusKey(MasterStatus.SELECTED.name(), null).size();
        long offersAccepted = accepted + activeSelected;

        double rate = offersMade > 0 ? (double) offersAccepted / offersMade : 0.0;
        return new OfferMetrics(rate, null, offersMade, offersAccepted);
    }

    private List<CoordinatorWorkloadItem> buildCoordinatorWorkload(LocalDateTime from) {
        Map<Long, CoordinatorWorkloadItem> map = new LinkedHashMap<>();
        for (Object[] row : candidateRepository.countCreatedByUserSince(from)) {
            Long id = (Long) row[0];
            String name = (String) row[1];
            long created = ((Number) row[2]).longValue();
            map.put(id, new CoordinatorWorkloadItem(id, name, created, 0));
        }
        for (Object[] row : candidateRepository.countCoordinatedByHr()) {
            Long id = (Long) row[0];
            String name = (String) row[1];
            long coordinated = ((Number) row[2]).longValue();
            CoordinatorWorkloadItem existing = map.get(id);
            if (existing == null) {
                map.put(id, new CoordinatorWorkloadItem(id, name, 0, coordinated));
            } else {
                map.put(id, new CoordinatorWorkloadItem(id, name, existing.candidatesCreated(), coordinated));
            }
        }
        return new ArrayList<>(map.values());
    }

    private RescheduleRate buildRescheduleRate(LocalDateTime from, LocalDateTime now) {
        long postponeCount = postponeRequestRepository.countCreatedSince(from);
        long interviews = interviewScheduleRepository.countScheduledBetween(from, now);
        double percent = interviews > 0 ? (postponeCount * 100.0 / interviews) : 0.0;
        List<NamedCount> byInterviewer = postponeRequestRepository.countByRequesterSince(from).stream()
                .map(row -> new NamedCount((Long) row[0], (String) row[1], ((Number) row[2]).longValue()))
                .toList();
        List<NamedCount> byCandidate = postponeRequestRepository.countByCandidateSince(from).stream()
                .filter(row -> row[0] != null)
                .map(row -> new NamedCount((Long) row[0], (String) row[1], ((Number) row[2]).longValue()))
                .toList();
        return new RescheduleRate(percent, byInterviewer, byCandidate);
    }

    private Map<Long, LocalDateTime> latestEvents(List<Candidate> candidates) {
        if (candidates.isEmpty()) return Map.of();
        Map<Long, LocalDateTime> map = new HashMap<>();
        for (Object[] row : pipelineEventRepository.findLatestEventAtByCandidateIds(
                candidates.stream().map(Candidate::getId).toList())) {
            if (row[0] != null && row[1] != null) {
                map.put((Long) row[0], (LocalDateTime) row[1]);
            }
        }
        return map;
    }

    private boolean matchesHrSchedule(InterviewSchedule s, Long hrId) {
        if (hrId == null) return true;
        Candidate c = s.getRequest() != null ? s.getRequest().getCandidate() : null;
        return c != null && c.getCoordinatedHr() != null && hrId.equals(c.getCoordinatedHr().getId());
    }

    private Candidate resolveCandidate(InterviewPostponeRequest p) {
        if (p.getInterviewRequest() != null && p.getInterviewRequest().getCandidate() != null) {
            return p.getInterviewRequest().getCandidate();
        }
        if (p.getInterviewSchedule() != null && p.getInterviewSchedule().getRequest() != null) {
            return p.getInterviewSchedule().getRequest().getCandidate();
        }
        return null;
    }

    private String resolveCandidateName(InterviewSchedule s) {
        if (s.getRequest() == null) return null;
        if (s.getRequest().getCandidate() != null) return s.getRequest().getCandidate().getName();
        return s.getRequest().getCandidateName();
    }

    private LocalDateTime tz(LocalDateTime utc, ZoneId zone) {
        return TimeZoneMapper.fromUtc(utc, zone);
    }

    private double hoursBetween(LocalDateTime start, LocalDateTime end) {
        if (start == null || end == null) return 0;
        return Duration.between(start, end).toMinutes() / 60.0;
    }

    private double percentile(List<Double> sorted, double p) {
        if (sorted == null || sorted.isEmpty()) return 0;
        int idx = (int) Math.min(sorted.size() - 1, Math.round((sorted.size() - 1) * p));
        return Math.round(sorted.get(idx) * 100.0) / 100.0;
    }
}
