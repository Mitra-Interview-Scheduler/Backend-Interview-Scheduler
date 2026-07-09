package com.nemal.service;

import com.google.api.services.calendar.Calendar;
import com.google.api.services.calendar.model.*;
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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class GoogleCalendarEventService {

    private static final Logger logger = LoggerFactory.getLogger(GoogleCalendarEventService.class);
    private static final DateTimeFormatter RFC3339 = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss");

    private final GoogleCalendarTokenService tokenService;

    public GoogleCalendarEventService(GoogleCalendarTokenService tokenService) {
        this.tokenService = tokenService;
    }

    public record CalendarEventResult(String eventId, String meetingLink) {}

    public record ListedCalendarEvent(Event event, String calendarName) {}

    public CalendarEventResult createAvailabilityEvent(
            User interviewer,
            String timeZone,
            LocalDateTime start,
            LocalDateTime end,
            String description) throws Exception {
        Calendar calendar = tokenService.buildCalendarClient(interviewer);
        Event event = baseAvailabilityEvent(timeZone, start, end, description);
        Event created = calendar.events()
                .insert("primary", event)
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
        Event event = baseAvailabilityEvent(timeZone, start, end, description);
        Event updated = calendar.events()
                .patch("primary", eventId, event)
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
        var deleteRequest = calendar.events().delete("primary", eventId);
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
            return listEventsFromVisibleCalendars(calendar, timeMin, timeMax);
        } catch (Exception e) {
            logger.warn("Failed to list all Google calendars for user {}, falling back to primary: {}",
                    interviewer.getId(), e.getMessage());
            return listEventsFromCalendar(calendar, "primary", "Primary", timeMin, timeMax);
        }
    }

    private List<ListedCalendarEvent> listEventsFromVisibleCalendars(
            Calendar calendar,
            com.google.api.client.util.DateTime timeMin,
            com.google.api.client.util.DateTime timeMax) throws Exception {
        CalendarList calendarList = calendar.calendarList()
                .list()
                .setMinAccessRole("reader")
                .setShowHidden(false)
                .execute();

        if (calendarList.getItems() == null || calendarList.getItems().isEmpty()) {
            return listEventsFromCalendar(calendar, "primary", "Primary", timeMin, timeMax);
        }

        Map<String, ListedCalendarEvent> deduped = new LinkedHashMap<>();
        for (CalendarListEntry entry : calendarList.getItems()) {
            if (entry == null || entry.getId() == null || entry.getId().isBlank()) {
                continue;
            }
            if (Boolean.FALSE.equals(entry.getSelected())) {
                continue;
            }

            String calendarName = entry.getSummaryOverride() != null && !entry.getSummaryOverride().isBlank()
                    ? entry.getSummaryOverride()
                    : entry.getSummary();
            if (calendarName == null || calendarName.isBlank()) {
                calendarName = entry.getId();
            }

            try {
                for (ListedCalendarEvent listedEvent : listEventsFromCalendar(
                        calendar, entry.getId(), calendarName, timeMin, timeMax)) {
                    String dedupeKey = dedupeKey(listedEvent.event(), entry.getId());
                    deduped.putIfAbsent(dedupeKey, listedEvent);
                }
            } catch (Exception e) {
                logger.warn("Failed to list Google Calendar events for calendar {}: {}",
                        entry.getId(), e.getMessage());
            }
        }

        return new ArrayList<>(deduped.values());
    }

    private List<ListedCalendarEvent> listEventsFromCalendar(
            Calendar calendar,
            String calendarId,
            String calendarName,
            com.google.api.client.util.DateTime timeMin,
            com.google.api.client.util.DateTime timeMax) throws Exception {
        Events result = calendar.events().list(calendarId)
                .setTimeMin(timeMin)
                .setTimeMax(timeMax)
                .setSingleEvents(true)
                .setOrderBy("startTime")
                .setMaxResults(250)
                .execute();

        List<ListedCalendarEvent> events = new ArrayList<>();
        if (result.getItems() == null) {
            return events;
        }
        for (Event event : result.getItems()) {
            events.add(new ListedCalendarEvent(event, calendarName));
        }
        return events;
    }

    private com.google.api.client.util.DateTime toGoogleDateTime(LocalDateTime utcDateTime) {
        ZoneId utc = ZoneOffset.UTC;
        return new com.google.api.client.util.DateTime(
                utcDateTime.atZone(utc).toInstant().toEpochMilli());
    }

    private String dedupeKey(Event event, String calendarId) {
        if (event.getICalUID() != null && !event.getICalUID().isBlank()) {
            return event.getICalUID();
        }
        return calendarId + ":" + event.getId();
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
            ZoneId zone = eventDateTime.getTimeZone() != null && !eventDateTime.getTimeZone().isBlank()
                    ? ZoneId.of(eventDateTime.getTimeZone())
                    : fallbackZone;
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
            boolean createNewIfMissing) throws Exception {
        Calendar calendar = tokenService.buildCalendarClient(interviewer);
        Event interviewEvent = buildInterviewEvent(timeZone, start, end, title, attendeeEmails);

        Event result;
        if (eventId != null && !eventId.isBlank()) {
            try {
                Event existing = calendar.events().get("primary", eventId).execute();
                interviewEvent.setId(eventId);
                interviewEvent.setSequence(existing.getSequence());
                result = calendar.events()
                        .update("primary", eventId, interviewEvent)
                        .setConferenceDataVersion(1)
                        .setSendUpdates("all")
                        .execute();
            } catch (Exception e) {
                if (!createNewIfMissing) {
                    throw e;
                }
                logger.warn("Failed to update event {} with interview guests, creating new event: {}",
                        eventId, e.getMessage());
                result = calendar.events()
                        .insert("primary", interviewEvent)
                        .setConferenceDataVersion(1)
                        .setSendUpdates("all")
                        .execute();
            }
        } else {
            result = calendar.events()
                    .insert("primary", interviewEvent)
                    .setConferenceDataVersion(1)
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
        Event busyEvent = baseBusyBlockEvent(timeZone, start, end, description);
        Event created = calendar.events().insert("primary", busyEvent).execute();
        return new CalendarEventResult(created.getId(), null);
    }

    public CalendarEventResult createPanelInterviewEvent(
            User organizer,
            String timeZone,
            LocalDateTime start,
            LocalDateTime end,
            String title,
            List<String> attendeeEmails) throws Exception {
        Calendar calendar = tokenService.buildCalendarClient(organizer);
        Event event = buildInterviewEvent(timeZone, start, end, title, attendeeEmails);
        Event created = calendar.events()
                .insert("primary", event)
                .setConferenceDataVersion(1)
                .setSendUpdates("all")
                .execute();
        return new CalendarEventResult(created.getId(), extractMeetLink(created));
    }

    public CalendarEventResult revertToAvailabilityEvent(
            User interviewer,
            String eventId,
            String timeZone,
            LocalDateTime start,
            LocalDateTime end,
            String description) throws Exception {
        Calendar calendar = tokenService.buildCalendarClient(interviewer);

        try {
            calendar.events()
                    .delete("primary", eventId)
                    .setSendUpdates("all")
                    .execute();
            logger.info("Deleted Google Calendar interview event {} and notified guests", eventId);
        } catch (Exception e) {
            if (!isDeletedOrMissingEvent(e)) {
                logger.warn("Failed to delete Google Calendar event {}, falling back to update: {}",
                        eventId, e.getMessage());
                return revertToBusyBlockViaUpdate(
                        calendar, eventId, timeZone, start, end, description);
            }
        }

        Event busyEvent = baseBusyBlockEvent(timeZone, start, end, description);
        Event created = calendar.events().insert("primary", busyEvent).execute();
        return new CalendarEventResult(created.getId(), null);
    }

    private CalendarEventResult revertToBusyBlockViaUpdate(
            Calendar calendar,
            String eventId,
            String timeZone,
            LocalDateTime start,
            LocalDateTime end,
            String description) throws Exception {
        Event existing = calendar.events().get("primary", eventId).execute();

        Event busyEvent = baseBusyBlockEvent(timeZone, start, end, description);
        busyEvent.setId(eventId);
        busyEvent.setSequence(existing.getSequence());
        busyEvent.setAttendees(new ArrayList<>());
        busyEvent.setConferenceData(null);
        busyEvent.setHangoutLink(null);
        busyEvent.setGuestsCanSeeOtherGuests(null);

        Event updated = calendar.events()
                .update("primary", eventId, busyEvent)
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
            List<String> attendeeEmails) {
        Event event = new Event();
        event.setSummary(title);
        event.setDescription("Interview scheduled via Mitra Interview Scheduler");
        event.setTransparency("opaque");
        event.setVisibility("private");
        event.setGuestsCanSeeOtherGuests(false);
        event.setStart(toEventDateTime(start, timeZone));
        event.setEnd(toEventDateTime(end, timeZone));

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
