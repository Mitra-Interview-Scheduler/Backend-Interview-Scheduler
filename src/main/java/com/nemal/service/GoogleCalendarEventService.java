package com.nemal.service;

import com.google.api.services.calendar.Calendar;
import com.google.api.services.calendar.model.*;
import com.nemal.entity.User;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
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
        if (eventId == null || eventId.isBlank()) {
            return;
        }
        Calendar calendar = tokenService.buildCalendarClient(interviewer);
        calendar.events().delete("primary", eventId).execute();
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
        Event event = buildInterviewEvent(timeZone, start, end, title, attendeeEmails);

        Event result;
        if (eventId != null && !eventId.isBlank()) {
            try {
                result = calendar.events()
                        .patch("primary", eventId, event)
                        .setConferenceDataVersion(1)
                        .setSendUpdates("all")
                        .execute();
            } catch (Exception e) {
                if (!createNewIfMissing) {
                    throw e;
                }
                logger.warn("Failed to patch event {}, creating new interview event", eventId);
                result = calendar.events()
                        .insert("primary", event)
                        .setConferenceDataVersion(1)
                        .setSendUpdates("all")
                        .execute();
            }
        } else {
            result = calendar.events()
                    .insert("primary", event)
                    .setConferenceDataVersion(1)
                    .setSendUpdates("all")
                    .execute();
        }

        return new CalendarEventResult(result.getId(), extractMeetLink(result));
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
        Event event = baseAvailabilityEvent(timeZone, start, end, description);
        event.setAttendees(new ArrayList<>());
        event.setConferenceData(null);

        Event updated = calendar.events()
                .patch("primary", eventId, event)
                .setConferenceDataVersion(1)
                .setSendUpdates("all")
                .execute();
        return new CalendarEventResult(updated.getId(), null);
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
        event.setDescription("Interview scheduled via Mitra");
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
