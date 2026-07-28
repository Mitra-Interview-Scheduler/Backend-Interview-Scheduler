package com.nemal.service;

import com.google.api.services.calendar.Calendar;
import com.google.api.services.calendar.model.*;
import com.nemal.dto.GoogleCalendarListItemDto;
import com.nemal.entity.User;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;

@Service
public class GoogleCalendarEventService {

    private static final Logger logger = LoggerFactory.getLogger(GoogleCalendarEventService.class);
    private static final DateTimeFormatter RFC3339 = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss");
    private static final int MAX_SELECTED_CALENDARS = 25;
    private static final int EVENT_PAGE_SIZE = 250;

    private final GoogleCalendarTokenService tokenService;

    public GoogleCalendarEventService(GoogleCalendarTokenService tokenService) {
        this.tokenService = tokenService;
    }

    public record CalendarEventResult(String eventId, String meetingLink) {}

    public record ListedCalendarEvent(Event event, String calendarName) {}

    /** A labelled URL rendered into the interview event description (JD, resume, resource link, CV fallback). */
    public record ResourceLink(String label, String url) {}

    /** Builds a Google Calendar attachment from a Drive file (the only attachment type Calendar supports). */
    public EventAttachment buildDriveAttachment(String fileId, String fileUrl, String mimeType, String title) {
        EventAttachment attachment = new EventAttachment();
        attachment.setFileId(fileId);
        attachment.setFileUrl(fileUrl);
        attachment.setMimeType(mimeType);
        attachment.setTitle(title);
        return attachment;
    }

    public List<GoogleCalendarListItemDto> listCalendarsForUser(User interviewer) throws Exception {
        Calendar calendar = tokenService.buildCalendarClient(interviewer);
        List<CalendarListEntry> entries = listAllCalendarEntries(calendar, false);
        Set<String> savedSelection = tokenService.findSelectedCalendarIds(interviewer)
                .map(HashSet::new)
                .orElse(null);

        List<GoogleCalendarListItemDto> result = new ArrayList<>();
        for (CalendarListEntry entry : entries) {
            if (entry == null || entry.getId() == null || entry.getId().isBlank()) continue;
            String name = entry.getSummaryOverride() != null && !entry.getSummaryOverride().isBlank()
                    ? entry.getSummaryOverride()
                    : entry.getSummary();
            if (name == null || name.isBlank()) {
                name = entry.getId();
            }
            boolean googleSelected = !Boolean.FALSE.equals(entry.getSelected());
            boolean selected = savedSelection != null
                    ? savedSelection.contains(entry.getId())
                    : googleSelected;
            result.add(new GoogleCalendarListItemDto(
                    entry.getId(),
                    name,
                    entry.getAccessRole(),
                    Boolean.TRUE.equals(entry.getPrimary()),
                    googleSelected,
                    selected
            ));
        }

        // If custom selection references "primary", mark the primary calendar selected.
        if (savedSelection != null && savedSelection.contains("primary")) {
            result = result.stream()
                    .map(item -> item.primary()
                            ? new GoogleCalendarListItemDto(
                                    item.id(), item.name(), item.accessRole(), true,
                                    item.googleSelected(), true)
                            : item)
                    .collect(Collectors.toCollection(ArrayList::new));
        }

        return result;
    }
    public CalendarEventResult createAvailabilityEvent(
            User interviewer,
            String timeZone,
            LocalDateTime start,
            LocalDateTime end,
            String description) throws Exception {
        Calendar calendar = tokenService.buildCalendarClient(interviewer);
        String calendarId = tokenService.resolveAppCalendarId(interviewer);
        Event event = baseAvailabilityEvent(timeZone, start, end, description);
        Event created = calendar.events()
                .insert(calendarId, event)
                .execute();
        return new CalendarEventResult(created.getId(), extractMeetLink(created));
    }

    public CalendarEventResult updateAvailabilityEvent(
            User interviewer,
            String eventId,
            String timeZone,
            LocalDateTime start,
            LocalDateTime end,
            String description) throws Exception {
        Calendar calendar = tokenService.buildCalendarClient(interviewer);
        String calendarId = tokenService.resolveAppCalendarId(interviewer);
        Event event = baseAvailabilityEvent(timeZone, start, end, description);
        Event updated = calendar.events()
                .patch(calendarId, eventId, event)
                .execute();
        return new CalendarEventResult(updated.getId(), extractMeetLink(updated));
    }

    public void deleteEvent(User interviewer, String eventId) throws Exception {
        deleteEvent(interviewer, eventId, false);
    }

    public void deleteEvent(User interviewer, String eventId, boolean notifyGuests) throws Exception {
        if (eventId == null || eventId.isBlank()) {
            return;
        }
        Calendar calendar = tokenService.buildCalendarClient(interviewer);
        String calendarId = tokenService.resolveAppCalendarId(interviewer);
        var deleteRequest = calendar.events().delete(calendarId, eventId);
        if (notifyGuests) {
            deleteRequest.setSendUpdates("all");
        }
        deleteRequest.execute();
    }

    public List<ListedCalendarEvent> listEventsInRange(
            User interviewer,
            LocalDateTime startUtc,
            LocalDateTime endUtc) throws Exception {
        Calendar calendar = tokenService.buildCalendarClient(interviewer);
        com.google.api.client.util.DateTime timeMin = toGoogleDateTime(startUtc);
        com.google.api.client.util.DateTime timeMax = toGoogleDateTime(endUtc);

        try {
            return listEventsFromVisibleCalendars(interviewer, calendar, timeMin, timeMax);
        } catch (Exception e) {
            logger.warn("Failed to list all Google calendars for user {}, falling back to primary: {}",
                    interviewer.getId(), e.getMessage());
            return listEventsFromCalendar(calendar, "primary", "Primary", timeMin, timeMax);
        }
    }

    private List<ListedCalendarEvent> listEventsFromVisibleCalendars(
            User interviewer,
            Calendar calendar,
            com.google.api.client.util.DateTime timeMin,
            com.google.api.client.util.DateTime timeMax) throws Exception {
        List<CalendarListEntry> allEntries = listAllCalendarEntries(calendar, false);
        if (allEntries.isEmpty()) {
            return listEventsFromCalendar(calendar, "primary", "Primary", timeMin, timeMax);
        }

        Optional<List<String>> customSelection = tokenService.findSelectedCalendarIds(interviewer);
        List<CalendarListEntry> selectedCalendars;

        if (customSelection.isPresent()) {
            List<String> wantedIds = customSelection.get();
            // Explicit empty selection → show no Google calendars on availability.
            if (wantedIds.isEmpty()) {
                return List.of();
            }

            Set<String> wanted = new HashSet<>(wantedIds);
            selectedCalendars = allEntries.stream()
                    .filter(entry -> entry != null && entry.getId() != null)
                    .filter(entry -> wanted.contains(entry.getId())
                            || (wanted.contains("primary") && Boolean.TRUE.equals(entry.getPrimary())))
                    .limit(MAX_SELECTED_CALENDARS)
                    .toList();

            // If IDs don't resolve (renamed/deleted), still try listing them directly.
            if (selectedCalendars.isEmpty()) {
                selectedCalendars = wantedIds.stream()
                        .limit(MAX_SELECTED_CALENDARS)
                        .map(id -> {
                            CalendarListEntry stub = new CalendarListEntry();
                            stub.setId(id);
                            stub.setSummary(id);
                            return stub;
                        })
                        .toList();
            }
        } else {
            selectedCalendars = allEntries.stream()
                    .filter(entry -> entry != null && entry.getId() != null && !entry.getId().isBlank())
                    .filter(entry -> !Boolean.FALSE.equals(entry.getSelected()))
                    .limit(MAX_SELECTED_CALENDARS)
                    .toList();
        }

        if (selectedCalendars.isEmpty()) {
            // Legacy (no Mitra selection saved): fall back to primary.
            return listEventsFromCalendar(calendar, "primary", "Primary", timeMin, timeMax);
        }

        Map<String, ListedCalendarEvent> deduped = new ConcurrentHashMap<>();

        try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
            List<CompletableFuture<Void>> futures = selectedCalendars.stream()
                    .map(entry -> CompletableFuture.runAsync(() -> {
                        String calendarName = entry.getSummaryOverride() != null && !entry.getSummaryOverride().isBlank()
                                ? entry.getSummaryOverride()
                                : entry.getSummary();
                        if (calendarName == null || calendarName.isBlank()) {
                            calendarName = entry.getId();
                        }

                        try {
                            Calendar perCalendarClient = tokenService.buildCalendarClient(interviewer);
                            for (ListedCalendarEvent listedEvent : listEventsFromCalendar(
                                    perCalendarClient, entry.getId(), calendarName, timeMin, timeMax)) {
                                String dedupeKey = dedupeKey(listedEvent.event(), entry.getId());
                                deduped.putIfAbsent(dedupeKey, listedEvent);
                            }
                        } catch (Exception e) {
                            logger.warn("Failed to list Google Calendar events for calendar {}: {}",
                                    entry.getId(), e.getMessage());
                        }
                    }, executor))
                    .toList();

            CompletableFuture.allOf(futures.toArray(CompletableFuture[]::new)).join();
        }

        return new ArrayList<>(deduped.values());
    }

    private List<CalendarListEntry> listAllCalendarEntries(Calendar calendar, boolean includeHidden)
            throws Exception {
        List<CalendarListEntry> entries = new ArrayList<>();
        String pageToken = null;
        do {
            CalendarList calendarList = calendar.calendarList()
                    .list()
                    .setMinAccessRole("reader")
                    .setShowHidden(includeHidden)
                    .setPageToken(pageToken)
                    .setMaxResults(250)
                    .execute();
            if (calendarList.getItems() != null) {
                entries.addAll(calendarList.getItems());
            }
            pageToken = calendarList.getNextPageToken();
        } while (pageToken != null && !pageToken.isBlank());
        return entries;
    }

    private List<ListedCalendarEvent> listEventsFromCalendar(
            Calendar calendar,
            String calendarId,
            String calendarName,
            com.google.api.client.util.DateTime timeMin,
            com.google.api.client.util.DateTime timeMax) throws Exception {
        List<ListedCalendarEvent> events = new ArrayList<>();
        String pageToken = null;
        do {
            Events result = calendar.events().list(calendarId)
                    .setTimeMin(timeMin)
                    .setTimeMax(timeMax)
                    .setSingleEvents(true)
                    .setOrderBy("startTime")
                    .setMaxResults(EVENT_PAGE_SIZE)
                    .setPageToken(pageToken)
                    .execute();

            if (result.getItems() != null) {
                for (Event event : result.getItems()) {
                    events.add(new ListedCalendarEvent(event, calendarName));
                }
            }
            pageToken = result.getNextPageToken();
        } while (pageToken != null && !pageToken.isBlank());

        return events;
    }

    private com.google.api.client.util.DateTime toGoogleDateTime(LocalDateTime utcDateTime) {
        ZoneId utc = ZoneOffset.UTC;
        return new com.google.api.client.util.DateTime(
                utcDateTime.atZone(utc).toInstant().toEpochMilli());
    }

    private String dedupeKey(Event event, String calendarId) {
        // Expanded recurring instances share the same iCalUID. Using iCalUID alone
        // drops every occurrence after the first. Prefer the unique instance event id.
        if (event.getId() != null && !event.getId().isBlank()) {
            return calendarId + ":" + event.getId();
        }
        if (event.getICalUID() != null && !event.getICalUID().isBlank()) {
            String startStamp = "";
            if (event.getStart() != null) {
                if (event.getStart().getDateTime() != null) {
                    startStamp = String.valueOf(event.getStart().getDateTime().getValue());
                } else if (event.getStart().getDate() != null) {
                    startStamp = event.getStart().getDate().toString();
                }
            }
            return event.getICalUID() + ":" + startStamp;
        }
        return calendarId + ":unknown";
    }

    public LocalDateTime parseEventStart(Event event, ZoneId fallbackZone) {
        return parseEventBoundary(event.getStart(), fallbackZone);
    }

    public LocalDateTime parseEventEnd(Event event, ZoneId fallbackZone) {
        LocalDateTime end = parseEventBoundary(event.getEnd(), fallbackZone);
        if (event.getStart() != null && event.getStart().getDate() != null && end != null) {
            // Google all-day events use an exclusive end date.
            return end;
        }
        return end;
    }

    private LocalDateTime parseEventBoundary(EventDateTime eventDateTime, ZoneId fallbackZone) {
        if (eventDateTime == null) {
            return null;
        }
        if (eventDateTime.getDateTime() != null) {
            // DateTime value is an absolute instant (UTC millis). Convert into the
            // interviewer's zone for LocalDateTime; do not re-interpret with the
            // event's timezone string or recurring instances can shift/drop.
            ZoneId zone = fallbackZone != null ? fallbackZone : ZoneOffset.UTC;
            return Instant.ofEpochMilli(eventDateTime.getDateTime().getValue())
                    .atZone(zone)
                    .toLocalDateTime();
        }
        if (eventDateTime.getDate() != null) {
            LocalDate date = LocalDate.parse(eventDateTime.getDate().toString());
            return date.atStartOfDay();
        }
        return null;
    }

    public CalendarEventResult bookInterviewEvent(
            User interviewer,
            String eventId,
            String timeZone,
            LocalDateTime start,
            LocalDateTime end,
            String title,
            List<String> attendeeEmails,
            boolean createNewIfMissing,
            List<ResourceLink> links,
            List<EventAttachment> attachments) throws Exception {
        return bookInterviewEvent(
                interviewer, eventId, timeZone, start, end, title, attendeeEmails,
                createNewIfMissing, links, attachments, null);
    }

    public CalendarEventResult bookInterviewEvent(
            User interviewer,
            String eventId,
            String timeZone,
            LocalDateTime start,
            LocalDateTime end,
            String title,
            List<String> attendeeEmails,
            boolean createNewIfMissing,
            List<ResourceLink> links,
            List<EventAttachment> attachments,
            String targetDesignation) throws Exception {
        Calendar calendar = tokenService.buildCalendarClient(interviewer);
        String calendarId = tokenService.resolveAppCalendarId(interviewer);
        Event interviewEvent = buildInterviewEvent(
                timeZone, start, end, title, attendeeEmails, links, attachments, targetDesignation);

        Event result;
        if (eventId != null && !eventId.isBlank()) {
            try {
                Event existing = calendar.events().get(calendarId, eventId).execute();
                interviewEvent.setId(eventId);
                interviewEvent.setSequence(existing.getSequence());
                result = calendar.events()
                        .update(calendarId, eventId, interviewEvent)
                        .setConferenceDataVersion(1)
                        .setSupportsAttachments(true)
                        .setSendUpdates("all")
                        .execute();
            } catch (Exception e) {
                if (!createNewIfMissing) {
                    throw e;
                }
                logger.warn("Failed to update event {} with interview guests, creating new event: {}",
                        eventId, e.getMessage());
                result = calendar.events()
                        .insert(calendarId, interviewEvent)
                        .setConferenceDataVersion(1)
                        .setSupportsAttachments(true)
                        .setSendUpdates("all")
                        .execute();
            }
        } else {
            result = calendar.events()
                    .insert(calendarId, interviewEvent)
                    .setConferenceDataVersion(1)
                    .setSupportsAttachments(true)
                    .setSendUpdates("all")
                    .execute();
        }

        return new CalendarEventResult(result.getId(), extractMeetLink(result));
    }

    public CalendarEventResult createBusyBlockEvent(
            User calendarOwner,
            String timeZone,
            LocalDateTime start,
            LocalDateTime end,
            String description) throws Exception {
        Calendar calendar = tokenService.buildCalendarClient(calendarOwner);
        String calendarId = tokenService.resolveAppCalendarId(calendarOwner);
        Event busyEvent = baseBusyBlockEvent(timeZone, start, end, description);
        Event created = calendar.events().insert(calendarId, busyEvent).execute();
        return new CalendarEventResult(created.getId(), null);
    }

    public CalendarEventResult createPanelInterviewEvent(
            User organizer,
            String timeZone,
            LocalDateTime start,
            LocalDateTime end,
            String title,
            List<String> attendeeEmails,
            List<ResourceLink> links,
            List<EventAttachment> attachments) throws Exception {
        return createPanelInterviewEvent(
                organizer, timeZone, start, end, title, attendeeEmails, links, attachments, null);
    }

    public CalendarEventResult createPanelInterviewEvent(
            User organizer,
            String timeZone,
            LocalDateTime start,
            LocalDateTime end,
            String title,
            List<String> attendeeEmails,
            List<ResourceLink> links,
            List<EventAttachment> attachments,
            String targetDesignation) throws Exception {
        Calendar calendar = tokenService.buildCalendarClient(organizer);
        String calendarId = tokenService.resolveAppCalendarId(organizer);
        Event event = buildInterviewEvent(
                timeZone, start, end, title, attendeeEmails, links, attachments, targetDesignation);
        Event created = calendar.events()
                .insert(calendarId, event)
                .setConferenceDataVersion(1)
                .setSupportsAttachments(true)
                .setSendUpdates("all")
                .execute();
        return new CalendarEventResult(created.getId(), extractMeetLink(created));
    }

    /**
     * Background enrichment: attach Drive files and append resource links to an existing
     * interview event without re-notifying guests.
     */
    public void enrichEventWithAttachments(
            User organizer,
            String eventId,
            List<ResourceLink> additionalLinks,
            List<EventAttachment> additionalAttachments) throws Exception {
        if (organizer == null || eventId == null || eventId.isBlank()) {
            return;
        }
        boolean hasLinks = additionalLinks != null && !additionalLinks.isEmpty();
        boolean hasAttachments = additionalAttachments != null && !additionalAttachments.isEmpty();
        if (!hasLinks && !hasAttachments) {
            return;
        }

        Calendar calendar = tokenService.buildCalendarClient(organizer);
        String calendarId = tokenService.resolveAppCalendarId(organizer);
        Event existing = calendar.events()
                .get(calendarId, eventId)
                .setFields("id,description,attachments")
                .execute();

        Event patch = new Event();

        if (hasAttachments) {
            List<EventAttachment> merged = new ArrayList<>();
            if (existing.getAttachments() != null) {
                merged.addAll(existing.getAttachments());
            }
            Set<String> existingFileIds = merged.stream()
                    .map(EventAttachment::getFileId)
                    .filter(id -> id != null && !id.isBlank())
                    .collect(Collectors.toSet());
            for (EventAttachment attachment : additionalAttachments) {
                if (attachment == null) {
                    continue;
                }
                String fileId = attachment.getFileId();
                if (fileId != null && !fileId.isBlank() && existingFileIds.contains(fileId)) {
                    continue;
                }
                merged.add(attachment);
                if (fileId != null && !fileId.isBlank()) {
                    existingFileIds.add(fileId);
                }
            }
            patch.setAttachments(merged);
        }

        if (hasLinks) {
            String description = existing.getDescription() != null && !existing.getDescription().isBlank()
                    ? existing.getDescription()
                    : "Interview scheduled via Mitra Interview Scheduler";
            StringBuilder sb = new StringBuilder(description);
            boolean hasResourcesHeader = description.contains("Candidate resources:");
            for (ResourceLink link : additionalLinks) {
                if (link == null || link.url() == null || link.url().isBlank()) {
                    continue;
                }
                String url = link.url().trim();
                if (description.contains(url) || sb.toString().contains(url)) {
                    continue;
                }
                if (!hasResourcesHeader) {
                    sb.append("\n\nCandidate resources:");
                    hasResourcesHeader = true;
                }
                String label = (link.label() != null && !link.label().isBlank()) ? link.label() : "Link";
                sb.append("\n• ").append(label).append(": ").append(url);
            }
            patch.setDescription(sb.toString());
        }

        calendar.events()
                .patch(calendarId, eventId, patch)
                .setSupportsAttachments(true)
                .setSendUpdates("none")
                .execute();
    }

    public CalendarEventResult revertToAvailabilityEvent(
            User interviewer,
            String eventId,
            String timeZone,
            LocalDateTime start,
            LocalDateTime end,
            String description) throws Exception {
        Calendar calendar = tokenService.buildCalendarClient(interviewer);
        String calendarId = tokenService.resolveAppCalendarId(interviewer);

        try {
            calendar.events()
                    .delete(calendarId, eventId)
                    .setSendUpdates("all")
                    .execute();
            logger.info("Deleted Google Calendar interview event {} and notified guests", eventId);
        } catch (Exception e) {
            if (!isDeletedOrMissingEvent(e)) {
                logger.warn("Failed to delete Google Calendar event {}, falling back to update: {}",
                        eventId, e.getMessage());
                return revertToBusyBlockViaUpdate(
                        calendar, calendarId, eventId, timeZone, start, end, description);
            }
        }

        Event busyEvent = baseBusyBlockEvent(timeZone, start, end, description);
        Event created = calendar.events().insert(calendarId, busyEvent).execute();
        return new CalendarEventResult(created.getId(), null);
    }

    private CalendarEventResult revertToBusyBlockViaUpdate(
            Calendar calendar,
            String calendarId,
            String eventId,
            String timeZone,
            LocalDateTime start,
            LocalDateTime end,
            String description) throws Exception {
        Event existing = calendar.events().get(calendarId, eventId).execute();

        Event busyEvent = baseBusyBlockEvent(timeZone, start, end, description);
        busyEvent.setId(eventId);
        busyEvent.setSequence(existing.getSequence());
        busyEvent.setAttendees(new ArrayList<>());
        busyEvent.setConferenceData(null);
        busyEvent.setHangoutLink(null);
        busyEvent.setGuestsCanSeeOtherGuests(null);

        Event updated = calendar.events()
                .update(calendarId, eventId, busyEvent)
                .setConferenceDataVersion(1)
                .setSendUpdates("all")
                .execute();
        return new CalendarEventResult(updated.getId(), null);
    }

    private boolean isDeletedOrMissingEvent(Exception e) {
        String message = e.getMessage() != null ? e.getMessage().toLowerCase() : "";
        return message.contains("404")
                || message.contains("410")
                || message.contains("not found")
                || message.contains("deleted");
    }

    private Event baseBusyBlockEvent(
            String timeZone,
            LocalDateTime start,
            LocalDateTime end,
            String description) {
        Event event = new Event();
        event.setSummary(description != null && !description.isBlank()
                ? description
                : "Available for Interview");
        event.setDescription("Interview availability slot managed by Mitra");
        event.setTransparency("opaque");
        event.setVisibility("private");
        event.setStart(toEventDateTime(start, timeZone));
        event.setEnd(toEventDateTime(end, timeZone));
        return event;
    }

    private Event baseAvailabilityEvent(
            String timeZone,
            LocalDateTime start,
            LocalDateTime end,
            String description) {
        Event event = new Event();
        event.setSummary(description != null && !description.isBlank()
                ? description
                : "Available for Interview");
        event.setDescription("Interview availability slot managed by Mitra");
        event.setTransparency("transparent");
        event.setVisibility("private");
        event.setStart(toEventDateTime(start, timeZone));
        event.setEnd(toEventDateTime(end, timeZone));
        return event;
    }

    private Event buildInterviewEvent(
            String timeZone,
            LocalDateTime start,
            LocalDateTime end,
            String title,
            List<String> attendeeEmails,
            List<ResourceLink> links,
            List<EventAttachment> attachments,
            String targetDesignation) {
        Event event = new Event();
        event.setSummary(title);
        event.setDescription(buildInterviewDescription(links, targetDesignation));
        event.setTransparency("opaque");
        event.setVisibility("private");
        event.setGuestsCanSeeOtherGuests(false);
        event.setStart(toEventDateTime(start, timeZone));
        event.setEnd(toEventDateTime(end, timeZone));

        if (attachments != null && !attachments.isEmpty()) {
            event.setAttachments(attachments);
        }

        List<EventAttendee> attendees = new ArrayList<>();
        for (String email : attendeeEmails) {
            if (email != null && !email.isBlank()) {
                EventAttendee attendee = new EventAttendee();
                attendee.setEmail(email.trim());
                attendees.add(attendee);
            }
        }
        event.setAttendees(attendees);

        ConferenceSolutionKey conferenceSolutionKey = new ConferenceSolutionKey();
        conferenceSolutionKey.setType("hangoutsMeet");
        CreateConferenceRequest createConferenceRequest = new CreateConferenceRequest();
        createConferenceRequest.setRequestId(UUID.randomUUID().toString());
        createConferenceRequest.setConferenceSolutionKey(conferenceSolutionKey);
        ConferenceData conferenceData = new ConferenceData();
        conferenceData.setCreateRequest(createConferenceRequest);
        event.setConferenceData(conferenceData);
        return event;
    }

    private String buildInterviewDescription(List<ResourceLink> links, String targetDesignation) {
        StringBuilder sb = new StringBuilder("Interview scheduled via Mitra Interview Scheduler");
        if (targetDesignation != null && !targetDesignation.isBlank()) {
            sb.append("\n\nTarget designation: ").append(targetDesignation.trim());
        }
        if (links != null) {
            List<ResourceLink> valid = links.stream()
                    .filter(l -> l != null && l.url() != null && !l.url().isBlank())
                    .toList();
            if (!valid.isEmpty()) {
                sb.append("\n\nCandidate resources:");
                for (ResourceLink link : valid) {
                    String label = (link.label() != null && !link.label().isBlank()) ? link.label() : "Link";
                    sb.append("\n• ").append(label).append(": ").append(link.url().trim());
                }
            }
        }
        return sb.toString();
    }

    private EventDateTime toEventDateTime(LocalDateTime dateTime, String timeZone) {
        ZoneId zoneId = ZoneId.of(timeZone != null && !timeZone.isBlank() ? timeZone : "UTC");
        EventDateTime eventDateTime = new EventDateTime();
        eventDateTime.setDateTime(new com.google.api.client.util.DateTime(
                dateTime.atZone(zoneId).format(RFC3339)));
        eventDateTime.setTimeZone(zoneId.getId());
        return eventDateTime;
    }

    private String extractMeetLink(Event event) {
        if (event.getConferenceData() != null
                && event.getConferenceData().getEntryPoints() != null) {
            return event.getConferenceData().getEntryPoints().stream()
                    .filter(entry -> "video".equalsIgnoreCase(entry.getEntryPointType()))
                    .map(EntryPoint::getUri)
                    .findFirst()
                    .orElse(null);
        }
        if (event.getHangoutLink() != null) {
            return event.getHangoutLink();
        }
        return null;
    }
}
