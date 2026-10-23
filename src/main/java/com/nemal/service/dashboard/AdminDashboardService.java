package com.nemal.service.dashboard;

import com.fasterxml.jackson.databind.JsonNode;
import com.nemal.dto.dashboard.AdminDashboardResponse;
import com.nemal.dto.dashboard.AdminDashboardResponse.*;
import com.nemal.dto.dashboard.CountedList;
import com.nemal.dto.dashboard.DashboardItemsResponse;
import com.nemal.dto.dashboard.DashboardPaging;
import com.nemal.entity.*;
import com.nemal.enums.Role;
import com.nemal.repository.*;
import com.nemal.util.DashboardClock;
import com.nemal.util.TimeZoneMapper;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.*;

@Service
public class AdminDashboardService {

    private final InterviewTypeRepository interviewTypeRepository;
    private final FeedbackFormRepository feedbackFormRepository;
    private final UserRepository userRepository;
    private final UserGoogleCalendarCredentialsRepository calendarCredentialsRepository;
    private final AvailabilitySlotRepository availabilitySlotRepository;
    private final CandidateRepository candidateRepository;
    private final EmailDeliveryLogRepository emailDeliveryLogRepository;
    private final DesignationRepository designationRepository;
    private final CandidateTechnologyRepository candidateTechnologyRepository;
    private final DomainRepository domainRepository;
    private final InterviewScheduleRepository interviewScheduleRepository;
    private final FeedbackResponseRepository feedbackResponseRepository;
    private final NotificationRepository notificationRepository;
    private final UserSettingsRepository userSettingsRepository;
    private final CandidateDocumentRepository candidateDocumentRepository;

    public AdminDashboardService(
            InterviewTypeRepository interviewTypeRepository,
            FeedbackFormRepository feedbackFormRepository,
            UserRepository userRepository,
            UserGoogleCalendarCredentialsRepository calendarCredentialsRepository,
            AvailabilitySlotRepository availabilitySlotRepository,
            CandidateRepository candidateRepository,
            EmailDeliveryLogRepository emailDeliveryLogRepository,
            DesignationRepository designationRepository,
            CandidateTechnologyRepository candidateTechnologyRepository,
            DomainRepository domainRepository,
            InterviewScheduleRepository interviewScheduleRepository,
            FeedbackResponseRepository feedbackResponseRepository,
            NotificationRepository notificationRepository,
            UserSettingsRepository userSettingsRepository,
            CandidateDocumentRepository candidateDocumentRepository
    ) {
        this.interviewTypeRepository = interviewTypeRepository;
        this.feedbackFormRepository = feedbackFormRepository;
        this.userRepository = userRepository;
        this.calendarCredentialsRepository = calendarCredentialsRepository;
        this.availabilitySlotRepository = availabilitySlotRepository;
        this.candidateRepository = candidateRepository;
        this.emailDeliveryLogRepository = emailDeliveryLogRepository;
        this.designationRepository = designationRepository;
        this.candidateTechnologyRepository = candidateTechnologyRepository;
        this.domainRepository = domainRepository;
        this.interviewScheduleRepository = interviewScheduleRepository;
        this.feedbackResponseRepository = feedbackResponseRepository;
        this.notificationRepository = notificationRepository;
        this.userSettingsRepository = userSettingsRepository;
        this.candidateDocumentRepository = candidateDocumentRepository;
    }

    @Transactional(readOnly = true)
    public AdminDashboardResponse getDashboard(int trendDays, int previewSize, ZoneId zone) {
        LocalDateTime now = DashboardClock.nowUtc();
        LocalDateTime trendFrom = now.minusDays(DashboardPaging.trendDays(trendDays));
        int preview = DashboardPaging.previewSize(previewSize);
        AlertLists alerts = collectAlerts(now);

        return new AdminDashboardResponse(
                toAlerts(alerts, 0, preview),
                buildLiveStatus(now, zone),
                buildTrends(trendFrom, now),
                TimeZoneMapper.fromUtc(now, zone)
        );
    }

    @Transactional(readOnly = true)
    public DashboardItemsResponse getDashboardItems(String section, Integer pageRaw, Integer sizeRaw) {
        int page = DashboardPaging.page(pageRaw);
        int size = DashboardPaging.size(sizeRaw);
        LocalDateTime now = DashboardClock.nowUtc();
        String key = section == null ? "" : section.trim();
        // Only the requested section is computed (was: every alert on every page click).
        return switch (key) {
            case "interviewersWithoutCalendar" ->
                    DashboardItemsResponse.of(key, CountedList.page(interviewerAlerts(now).withoutCalendar(), page, size));
            case "interviewersNoAvailability" ->
                    DashboardItemsResponse.of(key, CountedList.page(interviewerAlerts(now).noAvailability(), page, size));
            case "usersNoRoles" ->
                    DashboardItemsResponse.of(key, CountedList.page(usersWithoutRoles(), page, size));
            case "inactiveCoordinators" ->
                    DashboardItemsResponse.of(key, CountedList.page(inactiveCoordinators(), page, size));
            case "interviewTypesMissingForms" ->
                    DashboardItemsResponse.of(key, CountedList.page(interviewTypesMissingForms(), page, size));
            case "catalogGaps" ->
                    DashboardItemsResponse.of(key, CountedList.page(flattenCatalogGaps(catalogGaps()), page, size));
            default -> throw new IllegalArgumentException("Unknown dashboard section");
        };
    }

    private record AlertLists(
            List<MissingFormAlert> missingForms,
            List<UserAlert> withoutCalendar,
            List<UserAlert> noAvailability,
            List<UserAlert> noRoles,
            List<InactiveCoordinatorAlert> inactiveCoords,
            long emailFailures,
            CatalogGaps catalogGaps
    ) {}

    private record CatalogGapItem(String id, String group, String label) {}

    private Alerts toAlerts(AlertLists alerts, int page, int size) {
        return new Alerts(
                alerts.missingForms(),
                CountedList.page(alerts.withoutCalendar(), page, size),
                CountedList.page(alerts.noAvailability(), page, size),
                CountedList.page(alerts.noRoles(), page, size),
                CountedList.page(alerts.inactiveCoords(), page, size),
                new CountOnly(alerts.emailFailures()),
                alerts.catalogGaps()
        );
    }

    private List<CatalogGapItem> flattenCatalogGaps(CatalogGaps gaps) {
        List<CatalogGapItem> items = new ArrayList<>();
        if (gaps == null) return items;
        int i = 0;
        for (String item : safe(gaps.designationsWithoutInterviewers())) {
            items.add(new CatalogGapItem("desig-" + i++, "Designations without interviewers", item));
        }
        i = 0;
        for (String item : safe(gaps.orphanCandidateTechnologies())) {
            items.add(new CatalogGapItem("tech-" + i++, "Orphan candidate technologies", item));
        }
        i = 0;
        for (String item : safe(gaps.unusedDomains())) {
            items.add(new CatalogGapItem("domain-" + i++, "Unused domains", item));
        }
        return items;
    }

    private List<String> safe(List<String> list) {
        return list == null ? List.of() : list;
    }

    private AlertLists collectAlerts(LocalDateTime now) {
        InterviewerAlerts interviewerAlerts = interviewerAlerts(now);
        return new AlertLists(
                interviewTypesMissingForms(),
                interviewerAlerts.withoutCalendar(),
                interviewerAlerts.noAvailability(),
                usersWithoutRoles(),
                inactiveCoordinators(),
                emailDeliveryLogRepository.countByStatusAndSentAtAfter("FAILED", now.minusHours(24)),
                catalogGaps()
        );
    }

    private record InterviewerAlerts(List<UserAlert> withoutCalendar, List<UserAlert> noAvailability) {}

    private List<MissingFormAlert> interviewTypesMissingForms() {
        List<MissingFormAlert> missingForms = new ArrayList<>();
        List<FeedbackForm> activeForms = feedbackFormRepository.findAllByIsActiveTrue();
        for (InterviewType type : interviewTypeRepository.findByActiveTrueOrderByDisplayOrderAscLabelAsc()) {
            boolean covered = activeForms.stream().anyMatch(f -> formCoversType(f, type.getCode()));
            if (!covered) {
                missingForms.add(new MissingFormAlert(
                        type.getCode(),
                        type.getLabel(),
                        "No active feedback form matches this interview type"
                ));
            }
        }
        return missingForms;
    }

    private InterviewerAlerts interviewerAlerts(LocalDateTime now) {
        List<User> interviewers = userRepository.findActiveByRole(Role.INTERVIEWER);
        Set<Long> connectedIds = new HashSet<>(calendarCredentialsRepository.findAllConnectedUserIds());
        LocalDateTime to = now.plusDays(14);
        Set<Long> withSlots = new HashSet<>(
                availabilitySlotRepository.findInterviewerIdsWithOpenSlotsBetween(now, to));
        List<UserAlert> withoutCalendar = new ArrayList<>();
        List<UserAlert> noAvailability = new ArrayList<>();
        for (User u : interviewers) {
            if (!connectedIds.contains(u.getId())) {
                withoutCalendar.add(new UserAlert(u.getId(), u.getFullName(), u.getEmail()));
            }
            if (!withSlots.contains(u.getId())) {
                noAvailability.add(new UserAlert(u.getId(), u.getFullName(), u.getEmail()));
            }
        }
        return new InterviewerAlerts(withoutCalendar, noAvailability);
    }

    private List<UserAlert> usersWithoutRoles() {
        return userRepository.findUsersWithNoRoles().stream()
                .map(u -> new UserAlert(u.getId(), u.getFullName(), u.getEmail()))
                .toList();
    }

    /** Counted in the database (was: every active candidate loaded to check its coordinator). */
    private List<InactiveCoordinatorAlert> inactiveCoordinators() {
        return candidateRepository.countActiveCandidatesOfInactiveCoordinators().stream()
                .map(row -> new InactiveCoordinatorAlert(
                        (Long) row[0],
                        row[1] + " " + row[2],
                        ((Number) row[3]).longValue()))
                .toList();
    }

    private CatalogGaps catalogGaps() {
        List<String> designationsWithout = designationRepository.findActiveWithoutInterviewers().stream()
                .map(d -> {
                    String dept = d.getDepartment() != null ? d.getDepartment().getName() + " / " : "";
                    return dept + d.getName();
                })
                .toList();

        List<String> orphans = candidateTechnologyRepository.findOrphanActiveCandidateTechnologies().stream()
                .map(ct -> {
                    String cand = ct.getCandidate() != null ? ct.getCandidate().getName() : "?";
                    String tech = ct.getTechnology() != null ? ct.getTechnology().getName() : "?";
                    return cand + " → " + tech;
                })
                .distinct()
                .limit(50)
                .toList();

        List<String> unusedDomains = domainRepository.findUnusedActiveDomains().stream()
                .map(Domain::getName)
                .toList();

        return new CatalogGaps(designationsWithout, orphans, unusedDomains);
    }

    private boolean formCoversType(FeedbackForm form, String typeCode) {
        if (typeCode == null) return false;
        JsonNode arr = form.getInterviewTypesJson();
        if (arr == null || !arr.isArray() || arr.isEmpty()) {
            return true; // empty = match all
        }
        for (JsonNode n : arr) {
            if (n != null && typeCode.equalsIgnoreCase(n.asText())) {
                return true;
            }
        }
        return false;
    }

    private LiveStatus buildLiveStatus(LocalDateTime now, ZoneId zone) {
        // "Today" is the viewer's calendar day, converted to UTC for the queries.
        LocalDateTime dayStart = DashboardClock.startOfLocalDayUtc(now, zone, 0);
        LocalDateTime dayEnd = DashboardClock.startOfLocalDayUtc(now, zone, 1);

        ActivityToday activity = new ActivityToday(
                interviewScheduleRepository.countScheduledBetween(dayStart, dayEnd),
                feedbackResponseRepository.countSubmittedBetween(dayStart, dayEnd),
                candidateRepository.countCreatedBetween(dayStart, dayEnd),
                notificationRepository.countByCreatedAtAfter(dayStart)
        );

        LocalDateTime recentFrom = now.minusDays(30);
        List<RecentUser> recentlyAdded = userRepository.findCreatedSince(recentFrom, PageRequest.of(0, 10)).stream()
                .map(u -> new RecentUser(
                        u.getId(),
                        u.getFullName(),
                        u.getEmail(),
                        TimeZoneMapper.fromUtc(u.getCreatedAt(), zone)))
                .toList();

        // One grouped query instead of six counts: key "ROLE|active".
        Map<String, Long> roleCounts = new HashMap<>();
        for (Object[] row : userRepository.countByRoleAndActiveGrouped()) {
            roleCounts.put(row[0] + "|" + row[1], ((Number) row[2]).longValue());
        }
        UsersByRole usersByRole = new UsersByRole(
                roleCounts.getOrDefault(Role.ADMIN + "|true", 0L),
                roleCounts.getOrDefault(Role.ADMIN + "|false", 0L),
                roleCounts.getOrDefault(Role.HR + "|true", 0L),
                roleCounts.getOrDefault(Role.HR + "|false", 0L),
                roleCounts.getOrDefault(Role.INTERVIEWER + "|true", 0L),
                roleCounts.getOrDefault(Role.INTERVIEWER + "|false", 0L),
                userRepository.countMultiRoleUsers(),
                recentlyAdded
        );

        List<FormUsage> formUsage = feedbackResponseRepository.countByForm().stream()
                .map(row -> new FormUsage(
                        (Long) row[0],
                        (String) row[1],
                        row[2] != null ? ((Number) row[2]).intValue() : 0,
                        Boolean.TRUE.equals(row[3]),
                        ((Number) row[4]).longValue()
                ))
                .toList();

        List<TypeCount> notifByType = notificationRepository.countByTypeSince(dayStart).stream()
                .map(row -> new TypeCount(
                        row[0] != null ? (String) row[0] : "UNKNOWN",
                        ((Number) row[1]).longValue()))
                .toList();

        long optedOut = userSettingsRepository.countEmailOptedOut();
        long settingsCount = userSettingsRepository.countAllSettings();
        long userCount = Math.max(1, userRepository.count());
        // Opt-out rate among users with settings; if no settings, treat as 0
        double optOutRate = settingsCount > 0 ? (double) optedOut / settingsCount
                : 0.0;
        // Also consider users without settings as opted-in default
        if (userCount > 0 && settingsCount > 0) {
            optOutRate = (double) optedOut / userCount;
        }

        return new LiveStatus(activity, usersByRole, formUsage, notifByType,
                Math.round(optOutRate * 10000.0) / 10000.0);
    }

    private Trends buildTrends(LocalDateTime from, LocalDateTime now) {
        List<Object[]> loadRows = interviewScheduleRepository.countInterviewsByInterviewerSince(from);
        List<Long> counts = loadRows.stream()
                .map(r -> ((Number) r[2]).longValue())
                .sorted()
                .toList();
        double median = 0;
        if (!counts.isEmpty()) {
            median = counts.get(counts.size() / 2);
        }
        final double med = median;
        List<InterviewerLoad> load = loadRows.stream()
                .map(row -> {
                    long count = ((Number) row[2]).longValue();
                    String flag = null;
                    if (med > 0) {
                        if (count >= med * 1.5) flag = "HIGH";
                        else if (count <= Math.max(1, med * 0.5) && count < med) flag = "LOW";
                    } else if (count == 0) {
                        flag = "LOW";
                    }
                    return new InterviewerLoad((Long) row[0], (String) row[1], count, flag);
                })
                .toList();

        // Interviews in the trend window per candidate department, counted in the database.
        Map<Long, long[]> interviewByDept = new HashMap<>();
        for (Object[] row : interviewScheduleRepository.countInterviewsByDepartmentBetween(from, now.plusDays(1))) {
            interviewByDept.put((Long) row[0], new long[]{((Number) row[1]).longValue()});
        }

        List<DepartmentFunnel> funnels = new ArrayList<>();
        for (Object[] row : candidateRepository.departmentFunnelCounts()) {
            Long deptId = (Long) row[0];
            String name = (String) row[1];
            long candidates = ((Number) row[2]).longValue();
            long offers = ((Number) row[3]).longValue();
            long accepted = ((Number) row[4]).longValue();
            long interviews = interviewByDept.containsKey(deptId) ? interviewByDept.get(deptId)[0] : 0;
            funnels.add(new DepartmentFunnel(deptId, name, candidates, interviews, offers, accepted));
        }

        Double avgTurnaround = computeAvgTurnaround(from);
        Double panelDisagreement = computePanelDisagreement(from);

        long docs = candidateDocumentRepository.countByCreatedAtAfter(now.minusDays(30));

        return new Trends(
                load,
                funnels,
                new FeedbackQuality(avgTurnaround, panelDisagreement),
                new CalendarConflictBlocks("Not tracked historically; returns 0", 0),
                new DocumentUploads(docs)
        );
    }

    /** Average hours from interview end to feedback, computed by the database. */
    private Double computeAvgTurnaround(LocalDateTime from) {
        Double avg = feedbackResponseRepository.averageTurnaroundHoursSince(from);
        return avg == null ? null : Math.round(avg * 100.0) / 100.0;
    }

    private Double computePanelDisagreement(LocalDateTime from) {
        // Best-effort: compare recommendation values across panel feedback responses.
        // If unreliable/insufficient data, return null.
        try {
            // Only (panelId, answers) for panel feedback — no schedule/request entity graphs.
            Map<Long, List<String>> byPanel = new HashMap<>();
            for (Object[] row : feedbackResponseRepository.findPanelResponsesJsonSince(from)) {
                String rec = extractRecommendation((JsonNode) row[1]);
                if (row[0] == null || rec == null) continue;
                byPanel.computeIfAbsent((Long) row[0], k -> new ArrayList<>()).add(rec);
            }
            if (byPanel.isEmpty()) return null;
            long panelsWithMultiple = 0;
            long disagreements = 0;
            for (List<String> recs : byPanel.values()) {
                if (recs.size() < 2) continue;
                panelsWithMultiple++;
                Set<String> distinct = new HashSet<>(recs);
                if (distinct.size() > 1) disagreements++;
            }
            if (panelsWithMultiple == 0) return null;
            return Math.round((disagreements * 10000.0 / panelsWithMultiple)) / 10000.0;
        } catch (Exception e) {
            return null;
        }
    }

    private String extractRecommendation(JsonNode json) {
        if (json == null || !json.isObject()) return null;
        for (String key : List.of("recommendation", "overallRecommendation", "hireRecommendation")) {
            JsonNode n = json.get(key);
            if (n != null && !n.isNull() && n.isTextual() && !n.asText().isBlank()) {
                return n.asText().trim().toLowerCase(Locale.ROOT);
            }
        }
        Iterator<Map.Entry<String, JsonNode>> fields = json.fields();
        while (fields.hasNext()) {
            Map.Entry<String, JsonNode> e = fields.next();
            if (e.getKey() != null && e.getKey().toLowerCase(Locale.ROOT).contains("recommend")
                    && e.getValue() != null && e.getValue().isTextual()) {
                String v = e.getValue().asText();
                if (v != null && !v.isBlank()) return v.trim().toLowerCase(Locale.ROOT);
            }
        }
        return null;
    }
}
