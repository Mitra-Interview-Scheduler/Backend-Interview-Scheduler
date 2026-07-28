package com.nemal.service;

import com.google.api.services.calendar.model.Event;
import com.nemal.dto.GoogleCalendarAvailabilitySyncDto;
import com.nemal.dto.GoogleCalendarExternalEventDto;
import com.nemal.entity.*;
import com.nemal.enums.SlotStatus;
import com.nemal.repository.AvailabilitySlotRepository;
import com.nemal.repository.CandidateDocumentRepository;
import com.nemal.repository.CandidateRepository;
import com.nemal.repository.InterviewPanelRepository;
import com.nemal.repository.InterviewScheduleRepository;
import com.nemal.util.TimeZoneMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class CalendarSyncService {

    private static final Logger logger = LoggerFactory.getLogger(CalendarSyncService.class);

    private final GoogleCalendarEventService eventService;
    private final GoogleCalendarTokenService tokenService;
    private final UserSettingsService userSettingsService;
    private final AvailabilitySlotRepository availabilitySlotRepository;
    private final InterviewScheduleRepository interviewScheduleRepository;
    private final InterviewPanelRepository interviewPanelRepository;
    private final CandidateRepository candidateRepository;
    private final CalendarAttachmentSyncService calendarAttachmentSyncService;
    private final boolean calendarRequired;

    public CalendarSyncService(
            GoogleCalendarEventService eventService,
            GoogleCalendarTokenService tokenService,
            UserSettingsService userSettingsService,
            AvailabilitySlotRepository availabilitySlotRepository,
            InterviewScheduleRepository interviewScheduleRepository,
            InterviewPanelRepository interviewPanelRepository,
            CandidateRepository candidateRepository,
            CandidateDocumentRepository candidateDocumentRepository,
            GoogleDriveService driveService,
            CalendarAttachmentSyncService calendarAttachmentSyncService,
            @Value("${app.frontend.url:}") String frontendUrl,
            @Value("${google.calendar.required:false}") boolean calendarRequired) {
        this.eventService = eventService;
        this.tokenService = tokenService;
        this.userSettingsService = userSettingsService;
        this.availabilitySlotRepository = availabilitySlotRepository;
        this.interviewScheduleRepository = interviewScheduleRepository;
        this.interviewPanelRepository = interviewPanelRepository;
        this.candidateRepository = candidateRepository;
        this.calendarAttachmentSyncService = calendarAttachmentSyncService;
        this.calendarRequired = calendarRequired;
    }

    public void ensureInterviewerConnected(User interviewer) {
        if (!interviewer.hasInterviewerRole()) {
            return;
        }
        if (!tokenService.isConnected(interviewer)) {
            String message = "Google Calendar is not connected. Connect it from Settings before managing availability.";
            if (calendarRequired) {
                throw new RuntimeException(message);
            }
            logger.warn("Interviewer {} has no Google Calendar connection", interviewer.getId());
        }
    }

    @Transactional
    public int syncUnsyncedAvailabilitySlots(User interviewer) {
        if (!interviewer.hasInterviewerRole() || !tokenService.isConnected(interviewer)) {
            return 0;
        }

        List<AvailabilitySlot> unsyncedSlots = availabilitySlotRepository
                .findByInterviewerIdAndIsActiveTrue(interviewer.getId())
                .stream()
                .filter(slot -> slot.getGoogleCalendarEventId() == null)
                .filter(slot -> slot.getStatus() == SlotStatus.AVAILABLE)
                .toList();

        int syncedCount = 0;
        for (AvailabilitySlot slot : unsyncedSlots) {
            try {
                syncAvailabilitySlotCreated(slot);
                if (slot.getGoogleCalendarEventId() != null) {
                    syncedCount++;
                }
            } catch (Exception e) {
                logger.warn("Failed to backfill availability slot {} to Google Calendar: {}",
                        slot.getId(), e.getMessage());
            }
        }

        logger.info("Backfilled {} of {} availability slots to Google Calendar for user {}",
                syncedCount, unsyncedSlots.size(), interviewer.getId());
        return syncedCount;
    }

    @Transactional
    public GoogleCalendarAvailabilitySyncDto syncUnsyncedAvailabilitySlotsResult(User interviewer) {
        List<AvailabilitySlot> unsyncedSlots = availabilitySlotRepository
                .findByInterviewerIdAndIsActiveTrue(interviewer.getId())
                .stream()
                .filter(slot -> slot.getGoogleCalendarEventId() == null)
                .filter(slot -> slot.getStatus() == SlotStatus.AVAILABLE)
                .toList();

        int syncedCount = syncUnsyncedAvailabilitySlots(interviewer);
        return new GoogleCalendarAvailabilitySyncDto(syncedCount, unsyncedSlots.size());
    }

    public List<GoogleCalendarExternalEventDto> listExternalGoogleCalendarEvents(
            User interviewer,
            LocalDateTime utcStart,
            LocalDateTime utcEnd) {
        if (!interviewer.hasInterviewerRole() || !tokenService.isConnected(interviewer)) {
            return List.of();
        }

        Set<String> managedEventIds = availabilitySlotRepository
                .findByInterviewerIdAndStartDateTimeBetweenAndIsActiveTrue(
                        interviewer.getId(), utcStart, utcEnd)
                .stream()
                .map(AvailabilitySlot::getGoogleCalendarEventId)
                .filter(Objects::nonNull)
                .filter(id -> !id.isBlank())
                .collect(Collectors.toCollection(HashSet::new));

        try {
            ZoneId zone = ZoneId.of(resolveTimeZone(interviewer));
            List<GoogleCalendarEventService.ListedCalendarEvent> listedEvents =
                    eventService.listEventsInRange(interviewer, utcStart, utcEnd);
            List<GoogleCalendarExternalEventDto> externalEvents = new ArrayList<>();

            for (GoogleCalendarEventService.ListedCalendarEvent listedEvent : listedEvents) {
                Event event = listedEvent.event();
                if (event.getId() == null || managedEventIds.contains(event.getId())) {
                    continue;
                }
                if ("cancelled".equalsIgnoreCase(event.getStatus())) {
                    continue;
                }

                boolean allDay = event.getStart() != null && event.getStart().getDate() != null;
                LocalDateTime start = eventService.parseEventStart(event, zone);
                LocalDateTime end = eventService.parseEventEnd(event, zone);
                if (start == null || end == null) {
                    continue;
                }
                if (!allDay && !end.isAfter(start)) {
                    continue;
                }

                String title = event.getSummary();
                if (title == null || title.isBlank()) {
                    title = "(No title)";
                }

                externalEvents.add(new GoogleCalendarExternalEventDto(
                        event.getId(),
                        title,
                        TimeZoneMapper.toUtc(start, zone),
                        TimeZoneMapper.toUtc(end, zone),
                        allDay,
                        true,
                        listedEvent.calendarName()));
            }

            return externalEvents;
        } catch (Exception e) {
            logger.warn("Failed to list external Google Calendar events for user {}: {}",
                    interviewer.getId(), e.getMessage());
            return List.of();
        }
    }

    @Transactional
    public void syncAvailabilitySlotCreated(AvailabilitySlot slot) {
        User interviewer = slot.getInterviewer();
        if (!tokenService.isConnected(interviewer)) {
            ensureInterviewerConnected(interviewer);
            return;
        }
        if (slot.getGoogleCalendarEventId() != null) {
            return;
        }

        try {
            String timeZone = resolveTimeZone(interviewer);
            var result = eventService.createAvailabilityEvent(
                    interviewer,
                    timeZone,
                    slot.getStartDateTime(),
                    slot.getEndDateTime(),
                    slot.getDescription());
            slot.setGoogleCalendarEventId(result.eventId());
            availabilitySlotRepository.save(slot);
        } catch (Exception e) {
            logger.warn("Failed to sync availability slot {} to Google Calendar: {}", slot.getId(), e.getMessage());
            if (calendarRequired) {
                throw new RuntimeException("Failed to sync slot to Google Calendar: " + e.getMessage());
            }
        }
    }

    @Transactional
    public void syncAvailabilitySlotUpdated(AvailabilitySlot slot) {
        User interviewer = slot.getInterviewer();
        if (!tokenService.isConnected(interviewer)) {
            return;
        }

        try {
            String timeZone = resolveTimeZone(interviewer);
            if (slot.getGoogleCalendarEventId() == null) {
                syncAvailabilitySlotCreated(slot);
                return;
            }
            var result = eventService.updateAvailabilityEvent(
                    interviewer,
                    slot.getGoogleCalendarEventId(),
                    timeZone,
                    slot.getStartDateTime(),
                    slot.getEndDateTime(),
                    slot.getDescription());
            slot.setGoogleCalendarEventId(result.eventId());
            availabilitySlotRepository.save(slot);
        } catch (Exception e) {
            logger.warn("Failed to update Google Calendar event for slot {}: {}", slot.getId(), e.getMessage());
        }
    }

    @Transactional
    public void syncAvailabilitySlotDeleted(AvailabilitySlot slot) {
        User interviewer = slot.getInterviewer();
        if (!tokenService.isConnected(interviewer) || slot.getGoogleCalendarEventId() == null) {
            return;
        }

        try {
            eventService.deleteEvent(interviewer, slot.getGoogleCalendarEventId());
        } catch (Exception e) {
            logger.warn("Failed to delete Google Calendar event for slot {}: {}", slot.getId(), e.getMessage());
        }
    }

    /** Links + Drive-backed attachments used to enrich an interview event for a candidate. */
    private record EventEnrichment(
            List<GoogleCalendarEventService.ResourceLink> links,
            List<com.google.api.services.calendar.model.EventAttachment> attachments) {}

    /**
     * Fast path for event creation: JD / resume / resource links (and existing Drive URLs
     * as attachments). Candidate document files are uploaded to Drive in the background
     * after the Meet event exists — see {@link CalendarAttachmentSyncService}.
     */
    private EventEnrichment buildCandidateEnrichment(Candidate candidate, User organizer) {
        List<GoogleCalendarEventService.ResourceLink> links = new ArrayList<>();
        List<com.google.api.services.calendar.model.EventAttachment> attachments = new ArrayList<>();
        if (candidate == null) {
            return new EventEnrichment(links, attachments);
        }

        addLink(links, "Job description", candidate.getJdUrl());
        addLink(links, "Resume", candidate.getResumeUrl());
        addCandidateResourceLinks(links, attachments, candidate.getResourceLink());
        return new EventEnrichment(links, attachments);
    }

    private void queueDocumentAttachmentSync(
            User organizer,
            Candidate candidate,
            String eventId,
            List<String> shareWithEmails) {
        if (organizer == null || organizer.getId() == null
                || candidate == null || candidate.getId() == null
                || eventId == null || eventId.isBlank()) {
            return;
        }
        try {
            calendarAttachmentSyncService.syncCandidateDocumentsToEvent(
                    organizer.getId(),
                    candidate.getId(),
                    eventId,
                    shareWithEmails != null ? shareWithEmails : List.of());
        } catch (Exception e) {
            logger.warn("Failed to queue Drive attachment sync for event {}: {}", eventId, e.getMessage());
        }
    }

    /**
     * resourceLink may be a plain URL or a JSON array:
     * [{"url":"https://...","tag":"CV"}, ...]
     */
    private void addCandidateResourceLinks(
            List<GoogleCalendarEventService.ResourceLink> links,
            List<com.google.api.services.calendar.model.EventAttachment> attachments,
            String resourceLinkRaw) {
        if (resourceLinkRaw == null || resourceLinkRaw.isBlank()) {
            return;
        }
        String trimmed = resourceLinkRaw.trim();
        if (trimmed.startsWith("[")) {
            try {
                com.fasterxml.jackson.databind.JsonNode arr =
                        new com.fasterxml.jackson.databind.ObjectMapper().readTree(trimmed);
                if (!arr.isArray()) {
                    return;
                }
                for (com.fasterxml.jackson.databind.JsonNode item : arr) {
                    if (item == null || !item.isObject()) {
                        continue;
                    }
                    String url = item.path("url").asText(null);
                    String tag = item.path("tag").asText("Resource");
                    if (tag == null || tag.isBlank()) {
                        tag = "Resource";
                    }
                    addLink(links, tag, url);
                    tryAttachDriveUrl(attachments, tag, url);
                }
            } catch (Exception e) {
                logger.warn("Failed to parse candidate resourceLink JSON: {}", e.getMessage());
            }
            return;
        }
        addLink(links, "Resource link", trimmed);
        tryAttachDriveUrl(attachments, "Resource link", trimmed);
    }

    private void tryAttachDriveUrl(
            List<com.google.api.services.calendar.model.EventAttachment> attachments,
            String title,
            String url) {
        String fileId = extractGoogleDriveFileId(url);
        if (fileId == null) {
            return;
        }
        attachments.add(eventService.buildDriveAttachment(
                fileId,
                url.trim(),
                "application/vnd.google-apps.file",
                title));
    }

    private String extractGoogleDriveFileId(String url) {
        if (url == null || url.isBlank()) {
            return null;
        }
        String trimmed = url.trim();
        java.util.regex.Matcher fileMatcher = java.util.regex.Pattern
                .compile("/(?:file|document|spreadsheets|presentation)/d/([a-zA-Z0-9_-]+)")
                .matcher(trimmed);
        if (fileMatcher.find()) {
            return fileMatcher.group(1);
        }
        java.util.regex.Matcher openMatcher = java.util.regex.Pattern
                .compile("[?&]id=([a-zA-Z0-9_-]+)")
                .matcher(trimmed);
        if (openMatcher.find() && trimmed.contains("drive.google.com")) {
            return openMatcher.group(1);
        }
        return null;
    }

    private void addLink(List<GoogleCalendarEventService.ResourceLink> links, String label, String url) {
        if (url == null) {
            return;
        }
        String trimmed = url.trim();
        // Only linkify real URLs — some fields (e.g. jdUrl) may hold free text.
        if (trimmed.startsWith("http://") || trimmed.startsWith("https://")) {
            links.add(new GoogleCalendarEventService.ResourceLink(label, trimmed));
        }
    }

    @Transactional
    public void afterSingleInterviewBooked(
            AvailabilitySlot originalSlot,
            AvailabilitySlot bookedSlot,
            InterviewRequest request,
            InterviewSchedule schedule) {
        User interviewer = bookedSlot.getInterviewer();
        Candidate candidate = resolveCandidateForSync(request.getCandidate());
        User organizer = resolveInterviewOrganizer(interviewer, candidate);

        if (!tokenService.isConnected(organizer) && !tokenService.isConnected(interviewer)) {
            return;
        }
        if (!tokenService.isConnected(organizer)) {
            organizer = interviewer;
        }

        try {
            boolean partialBooking = !originalSlot.getId().equals(bookedSlot.getId());
            List<String> attendees = buildInterviewAttendees(request, organizer);
            // When Candidate Coordinator / Coordinated HR owns the Meet event, the
            // interviewer must still be invited as a guest (not only get a busy block).
            ensureGuestEmail(attendees, interviewer, organizer);
            String title = buildInterviewEventTitle("Interview", request.getCandidateName(), request, candidate);
            String targetDesignation = resolveCandidatePosition(request, candidate);
            EventEnrichment enrichment = buildCandidateEnrichment(candidate, organizer);
            logger.info(
                    "Booking Google Calendar interview for request {} with organizer {} and guest(s) {}",
                    request.getId(),
                    organizer.getId(),
                    attendees);

            if (isSameUser(organizer, interviewer)) {
                if (partialBooking) {
                    if (originalSlot.getGoogleCalendarEventId() != null) {
                        eventService.deleteEvent(interviewer, originalSlot.getGoogleCalendarEventId());
                        originalSlot.setGoogleCalendarEventId(null);
                        availabilitySlotRepository.save(originalSlot);
                    }
                    syncNewAvailabilityFragments(interviewer, originalSlot.getStartDateTime(), originalSlot.getEndDateTime());
                }

                String timeZone = resolveTimeZone(interviewer);
                var result = eventService.bookInterviewEvent(
                        interviewer,
                        partialBooking ? null : bookedSlot.getGoogleCalendarEventId(),
                        timeZone,
                        bookedSlot.getStartDateTime(),
                        bookedSlot.getEndDateTime(),
                        title,
                        attendees,
                        true,
                        enrichment.links(),
                        enrichment.attachments(),
                        targetDesignation);

                bookedSlot.setGoogleCalendarEventId(result.eventId());
                availabilitySlotRepository.save(bookedSlot);
                schedule.setGoogleCalendarEventId(result.eventId());
                schedule.setMeetingLink(result.meetingLink());
                interviewScheduleRepository.save(schedule);
                queueDocumentAttachmentSync(organizer, candidate, result.eventId(), attendees);
                return;
            }

            syncInterviewerBusyBlock(interviewer, bookedSlot, partialBooking ? originalSlot : null, title);
            String organizerTimeZone = resolveTimeZone(organizer);
            var result = eventService.bookInterviewEvent(
                    organizer,
                    null,
                    organizerTimeZone,
                    bookedSlot.getStartDateTime(),
                    bookedSlot.getEndDateTime(),
                    title,
                    attendees,
                    true,
                    enrichment.links(),
                    enrichment.attachments(),
                    targetDesignation);

            schedule.setGoogleCalendarEventId(result.eventId());
            schedule.setMeetingLink(result.meetingLink());
            interviewScheduleRepository.save(schedule);
            queueDocumentAttachmentSync(organizer, candidate, result.eventId(), attendees);
        } catch (Exception e) {
            logger.warn("Failed to book Google Calendar interview for request {}: {}", request.getId(), e.getMessage());
        }
    }

    @Transactional
    public void cancelSingleInterview(
            InterviewRequest request,
            InterviewSchedule schedule,
            AvailabilitySlot restoredSlot) {
        if (schedule != null) {
            schedule.setMeetingLink(null);
            interviewScheduleRepository.save(schedule);
        }

        User interviewer = restoredSlot.getInterviewer();
        Candidate candidate = resolveCandidateForSync(request.getCandidate());
        User organizer = resolveInterviewOrganizer(interviewer, candidate);
        if (!tokenService.isConnected(organizer)) {
            organizer = interviewer;
        }

        if (!tokenService.isConnected(interviewer) && !tokenService.isConnected(organizer)) {
            return;
        }

        try {
            String scheduleEventId = schedule != null ? schedule.getGoogleCalendarEventId() : null;
            if (!isSameUser(organizer, interviewer) && scheduleEventId != null && tokenService.isConnected(organizer)) {
                eventService.deleteEvent(organizer, scheduleEventId, true);
                if (schedule != null) {
                    schedule.setGoogleCalendarEventId(null);
                    interviewScheduleRepository.save(schedule);
                }
            }

            if (!tokenService.isConnected(interviewer)) {
                return;
            }

            String slotEventId = restoredSlot.getGoogleCalendarEventId();
            if (slotEventId == null && scheduleEventId != null && isSameUser(organizer, interviewer)) {
                slotEventId = scheduleEventId;
            }
            if (slotEventId == null) {
                syncAvailabilitySlotCreated(restoredSlot);
                return;
            }

            String timeZone = resolveTimeZone(interviewer);
            var result = eventService.revertToAvailabilityEvent(
                    interviewer,
                    slotEventId,
                    timeZone,
                    restoredSlot.getStartDateTime(),
                    restoredSlot.getEndDateTime(),
                    "Available for Interview");

            restoredSlot.setGoogleCalendarEventId(result.eventId());
            availabilitySlotRepository.save(restoredSlot);

            if (schedule != null && isSameUser(organizer, interviewer)) {
                schedule.setGoogleCalendarEventId(result.eventId());
                schedule.setMeetingLink(null);
                interviewScheduleRepository.save(schedule);
            }
        } catch (Exception e) {
            logger.warn("Failed to cancel Google Calendar interview for request {}: {}", request.getId(), e.getMessage());
        }
    }

    @Transactional
    public void afterPanelInterviewBooked(
            InterviewPanel panel,
            List<InterviewRequest> requests,
            List<AvailabilitySlot> bookedSlots) {
        if (requests.isEmpty() || bookedSlots.isEmpty()) {
            return;
        }

        User leadInterviewer = null;
        for (AvailabilitySlot bookedSlot : bookedSlots) {
            User interviewer = bookedSlot.getInterviewer();
            if (interviewer != null && tokenService.isConnected(interviewer)) {
                leadInterviewer = interviewer;
                break;
            }
        }

        InterviewPanel panelForSync = interviewPanelRepository.findByIdWithDetails(panel.getId()).orElse(panel);
        Candidate candidate = resolveCandidateForSync(panelForSync.getCandidate());
        User organizer = resolveInterviewOrganizer(leadInterviewer, candidate);

        if (organizer == null || !tokenService.isConnected(organizer)) {
            logger.warn(
                    "Panel {} not synced to Google Calendar: no connected HR coordinator or interviewer calendar",
                    panel.getId());
            return;
        }

        try {
            String title = buildInterviewEventTitle(
                    "Panel Interview",
                    panelForSync.getCandidateName(),
                    requests != null && !requests.isEmpty() ? requests.get(0) : null,
                    candidate);
            String targetDesignation = resolveCandidatePosition(
                    requests != null && !requests.isEmpty() ? requests.get(0) : null,
                    candidate);
            List<String> attendees = buildPanelAttendees(requests, panelForSync, organizer);
            if (bookedSlots != null) {
                for (AvailabilitySlot bookedSlot : bookedSlots) {
                    ensureGuestEmail(attendees, bookedSlot.getInterviewer(), organizer);
                }
            }
            EventEnrichment enrichment = buildCandidateEnrichment(candidate, organizer);
            logger.info(
                    "Creating panel Google Calendar event for panel {} with organizer {} and guest(s) {}",
                    panel.getId(),
                    organizer.getId(),
                    attendees);

            if (isSameUser(organizer, leadInterviewer)) {
                for (AvailabilitySlot bookedSlot : bookedSlots) {
                    User interviewer = bookedSlot.getInterviewer();
                    if (bookedSlot.getGoogleCalendarEventId() != null && tokenService.isConnected(interviewer)) {
                        eventService.deleteEvent(interviewer, bookedSlot.getGoogleCalendarEventId());
                        bookedSlot.setGoogleCalendarEventId(null);
                        availabilitySlotRepository.save(bookedSlot);
                    }
                }

                String timeZone = resolveTimeZone(organizer);
                var result = eventService.createPanelInterviewEvent(
                        organizer,
                        timeZone,
                        panelForSync.getStartDateTime(),
                        panelForSync.getEndDateTime(),
                        title,
                        attendees,
                        enrichment.links(),
                        enrichment.attachments(),
                        targetDesignation);

                panel.setGoogleCalendarEventId(result.eventId());
                panel.setMeetingLink(result.meetingLink());
                interviewPanelRepository.save(panel);

                for (InterviewRequest request : requests) {
                    interviewScheduleRepository.findActiveByRequestId(request.getId()).ifPresent(schedule -> {
                        schedule.setGoogleCalendarEventId(result.eventId());
                        schedule.setMeetingLink(result.meetingLink());
                        interviewScheduleRepository.save(schedule);

                        if (request.getAvailabilitySlot() != null) {
                            AvailabilitySlot slot = request.getAvailabilitySlot();
                            slot.setGoogleCalendarEventId(result.eventId());
                            availabilitySlotRepository.save(slot);
                        }
                    });
                }
                queueDocumentAttachmentSync(organizer, candidate, result.eventId(), attendees);
                return;
            }

            for (AvailabilitySlot bookedSlot : bookedSlots) {
                User interviewer = bookedSlot.getInterviewer();
                if (interviewer == null || !tokenService.isConnected(interviewer)) {
                    continue;
                }
                syncInterviewerBusyBlock(interviewer, bookedSlot, null, title);
            }

            String organizerTimeZone = resolveTimeZone(organizer);
            var result = eventService.createPanelInterviewEvent(
                    organizer,
                    organizerTimeZone,
                    panelForSync.getStartDateTime(),
                    panelForSync.getEndDateTime(),
                    title,
                    attendees,
                    enrichment.links(),
                    enrichment.attachments(),
                    targetDesignation);

            panel.setGoogleCalendarEventId(result.eventId());
            panel.setMeetingLink(result.meetingLink());
            interviewPanelRepository.save(panel);

            for (InterviewRequest request : requests) {
                interviewScheduleRepository.findActiveByRequestId(request.getId()).ifPresent(schedule -> {
                    schedule.setGoogleCalendarEventId(result.eventId());
                    schedule.setMeetingLink(result.meetingLink());
                    interviewScheduleRepository.save(schedule);
                });
            }
            queueDocumentAttachmentSync(organizer, candidate, result.eventId(), attendees);
        } catch (Exception e) {
            logger.warn("Failed to book Google Calendar panel {}: {}", panel.getId(), e.getMessage());
        }
    }

    @Transactional
    public void cancelPanelInterview(
            InterviewPanel panel,
            List<InterviewRequest> requests,
            List<AvailabilitySlot> restoredSlots) {
        User leadInterviewer = restoredSlots.isEmpty() ? null : restoredSlots.get(0).getInterviewer();
        InterviewPanel panelForSync = interviewPanelRepository.findByIdWithDetails(panel.getId()).orElse(panel);
        Candidate candidate = resolveCandidateForSync(panelForSync.getCandidate());
        User organizer = leadInterviewer != null
                ? resolveInterviewOrganizer(leadInterviewer, candidate)
                : null;
        if (organizer == null || !tokenService.isConnected(organizer)) {
            organizer = leadInterviewer;
        }

        boolean hrOrganized = organizer != null
                && leadInterviewer != null
                && !isSameUser(organizer, leadInterviewer);

        if (panel.getGoogleCalendarEventId() != null && organizer != null && tokenService.isConnected(organizer)) {
            try {
                eventService.deleteEvent(organizer, panel.getGoogleCalendarEventId(), true);
            } catch (Exception e) {
                logger.warn("Failed to delete panel Google event {}: {}", panel.getId(), e.getMessage());
            }
        }

        panel.setGoogleCalendarEventId(null);
        panel.setMeetingLink(null);
        interviewPanelRepository.save(panel);

        for (AvailabilitySlot restoredSlot : restoredSlots) {
            User interviewer = restoredSlot.getInterviewer();
            if (hrOrganized && restoredSlot.getGoogleCalendarEventId() != null
                    && interviewer != null && tokenService.isConnected(interviewer)) {
                try {
                    eventService.deleteEvent(interviewer, restoredSlot.getGoogleCalendarEventId());
                } catch (Exception e) {
                    logger.warn("Failed to delete interviewer busy block for slot {}: {}",
                            restoredSlot.getId(), e.getMessage());
                }
            }
            restoredSlot.setGoogleCalendarEventId(null);
            availabilitySlotRepository.save(restoredSlot);
            if (interviewer != null && tokenService.isConnected(interviewer)) {
                syncAvailabilitySlotCreated(restoredSlot);
            }
        }

        for (InterviewRequest request : requests) {
            interviewScheduleRepository.findActiveByRequestId(request.getId()).ifPresent(schedule -> {
                schedule.setGoogleCalendarEventId(null);
                schedule.setMeetingLink(null);
                interviewScheduleRepository.save(schedule);
            });
        }
    }

    private void syncNewAvailabilityFragments(
            User interviewer,
            java.time.LocalDateTime rangeStart,
            java.time.LocalDateTime rangeEnd) {
        List<AvailabilitySlot> fragments = availabilitySlotRepository
                .findByInterviewerIdAndStartDateTimeBetweenAndIsActiveTrue(
                        interviewer.getId(), rangeStart, rangeEnd)
                .stream()
                .filter(slot -> slot.getStatus() == SlotStatus.AVAILABLE)
                .filter(slot -> slot.getGoogleCalendarEventId() == null)
                .toList();

        for (AvailabilitySlot fragment : fragments) {
            syncAvailabilitySlotCreated(fragment);
        }
    }

    private List<String> buildInterviewAttendees(InterviewRequest request, User organizer) {
        Set<String> emails = new LinkedHashSet<>();
        String candidateEmail = resolveInviteEmail(request.getCandidateInviteEmail(), request.getCandidate());
        if (candidateEmail != null) {
            emails.add(candidateEmail);
        }
        if (request.getAssignedInterviewer() != null
                && request.getAssignedInterviewer().getEmail() != null
                && !isSameUser(organizer, request.getAssignedInterviewer())) {
            emails.add(request.getAssignedInterviewer().getEmail().trim());
        }
        if (request.getInterviewCoordinator() != null
                && request.getInterviewCoordinator().getEmail() != null
                && !isSameUser(organizer, request.getInterviewCoordinator())) {
            emails.add(request.getInterviewCoordinator().getEmail().trim());
        }
        if (request.getCandidate() != null
                && request.getCandidate().getCoordinatedHr() != null
                && request.getCandidate().getCoordinatedHr().getEmail() != null
                && !isSameUser(organizer, request.getCandidate().getCoordinatedHr())) {
            emails.add(request.getCandidate().getCoordinatedHr().getEmail().trim());
        }
        return new ArrayList<>(emails);
    }

    private String resolveCandidateEmail(Candidate candidate) {
        if (candidate == null || candidate.getId() == null) {
            return null;
        }
        return candidateRepository.findById(candidate.getId())
                .map(Candidate::getEmail)
                .map(String::trim)
                .filter(email -> !email.isBlank())
                .orElse(null);
    }

    private String resolveInviteEmail(String storedInviteEmail, Candidate candidate) {
        if (storedInviteEmail != null && !storedInviteEmail.isBlank()) {
            return storedInviteEmail.trim();
        }
        return resolveCandidateEmail(candidate);
    }

    private List<String> buildPanelAttendees(List<InterviewRequest> requests, InterviewPanel panel, User organizer) {
        Set<String> emails = new LinkedHashSet<>();
        String candidateEmail = resolveInviteEmail(panel.getCandidateInviteEmail(), panel.getCandidate());
        if (candidateEmail != null) {
            emails.add(candidateEmail);
        }
        for (InterviewRequest request : requests) {
            if (request.getAssignedInterviewer() != null
                    && request.getAssignedInterviewer().getEmail() != null
                    && !isSameUser(organizer, request.getAssignedInterviewer())) {
                emails.add(request.getAssignedInterviewer().getEmail().trim());
            }
        }
        if (panel.getInterviewCoordinator() != null
                && panel.getInterviewCoordinator().getEmail() != null
                && !isSameUser(organizer, panel.getInterviewCoordinator())) {
            emails.add(panel.getInterviewCoordinator().getEmail().trim());
        }
        if (panel.getCandidate() != null
                && panel.getCandidate().getCoordinatedHr() != null
                && panel.getCandidate().getCoordinatedHr().getEmail() != null
                && !isSameUser(organizer, panel.getCandidate().getCoordinatedHr())) {
            emails.add(panel.getCandidate().getCoordinatedHr().getEmail().trim());
        }
        return new ArrayList<>(emails);
    }

    private User resolveInterviewOrganizer(User fallbackInterviewer, Candidate candidate) {
        if (candidate != null && candidate.getCoordinatedHr() != null) {
            User coordinatedHr = candidate.getCoordinatedHr();
            if (tokenService.isConnected(coordinatedHr)) {
                logger.info(
                        "Using candidate HR coordinator {} as Google Calendar organizer for interview",
                        coordinatedHr.getId());
                return coordinatedHr;
            }
            if (fallbackInterviewer != null) {
                logger.info(
                        "Candidate HR coordinator {} is not connected to Google Calendar; interviewer {} remains organizer",
                        coordinatedHr.getId(),
                        fallbackInterviewer.getId());
            }
        }
        return fallbackInterviewer;
    }

    private Candidate resolveCandidateForSync(Candidate candidate) {
        if (candidate == null || candidate.getId() == null) {
            return null;
        }
        return candidateRepository.findByIdWithCoordinatedHr(candidate.getId()).orElse(candidate);
    }

    /**
     * Google Calendar subject, e.g. "Interview - Senior Software Engineer - Jane Doe".
     * Prefers the designation chosen on the interview request, then the candidate's target role.
     */
    private String buildInterviewEventTitle(
            String prefix,
            String candidateName,
            InterviewRequest request,
            Candidate candidate) {
        String name = candidateName != null && !candidateName.isBlank()
                ? candidateName.trim()
                : "Candidate";
        String position = resolveCandidatePosition(request, candidate);
        if (position == null || position.isBlank()) {
            return prefix + " - " + name;
        }
        return prefix + " - " + position + " - " + name;
    }

    private String resolveCandidatePosition(InterviewRequest request, Candidate candidate) {
        if (request != null && request.getCandidateDesignation() != null
                && request.getCandidateDesignation().getName() != null
                && !request.getCandidateDesignation().getName().isBlank()) {
            return request.getCandidateDesignation().getName().trim();
        }
        if (candidate != null && candidate.getTargetDesignation() != null
                && candidate.getTargetDesignation().getName() != null
                && !candidate.getTargetDesignation().getName().isBlank()) {
            return candidate.getTargetDesignation().getName().trim();
        }
        return null;
    }

    private void syncInterviewerBusyBlock(
            User interviewer,
            AvailabilitySlot bookedSlot,
            AvailabilitySlot originalSlot,
            String title) throws Exception {
        if (!tokenService.isConnected(interviewer)) {
            return;
        }

        if (originalSlot != null) {
            if (originalSlot.getGoogleCalendarEventId() != null) {
                eventService.deleteEvent(interviewer, originalSlot.getGoogleCalendarEventId());
                originalSlot.setGoogleCalendarEventId(null);
                availabilitySlotRepository.save(originalSlot);
            }
            syncNewAvailabilityFragments(interviewer, originalSlot.getStartDateTime(), originalSlot.getEndDateTime());
        }

        if (bookedSlot.getGoogleCalendarEventId() != null) {
            eventService.deleteEvent(interviewer, bookedSlot.getGoogleCalendarEventId());
        }

        String interviewerTimeZone = resolveTimeZone(interviewer);
        var busyResult = eventService.createBusyBlockEvent(
                interviewer,
                interviewerTimeZone,
                bookedSlot.getStartDateTime(),
                bookedSlot.getEndDateTime(),
                title);
        bookedSlot.setGoogleCalendarEventId(busyResult.eventId());
        availabilitySlotRepository.save(bookedSlot);
    }

    private void ensureGuestEmail(List<String> attendees, User guest, User organizer) {
        if (attendees == null || guest == null || guest.getEmail() == null || guest.getEmail().isBlank()) {
            return;
        }
        if (isSameUser(organizer, guest)) {
            return;
        }
        String email = guest.getEmail().trim();
        boolean alreadyPresent = attendees.stream()
                .filter(Objects::nonNull)
                .anyMatch(existing -> existing.equalsIgnoreCase(email));
        if (!alreadyPresent) {
            attendees.add(email);
        }
    }

    private boolean isSameUser(User left, User right) {
        return left != null && right != null && Objects.equals(left.getId(), right.getId());
    }

    private String resolveTimeZone(User user) {
        return userSettingsService.resolveTimezoneForCalendarSync(user);
    }
}
