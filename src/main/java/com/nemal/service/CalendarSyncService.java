package com.nemal.service;

import com.nemal.entity.*;
import com.nemal.enums.SlotStatus;
import com.nemal.repository.AvailabilitySlotRepository;
import com.nemal.repository.InterviewPanelRepository;
import com.nemal.repository.InterviewScheduleRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

@Service
public class CalendarSyncService {

    private static final Logger logger = LoggerFactory.getLogger(CalendarSyncService.class);

    private final GoogleCalendarEventService eventService;
    private final GoogleCalendarTokenService tokenService;
    private final UserSettingsService userSettingsService;
    private final AvailabilitySlotRepository availabilitySlotRepository;
    private final InterviewScheduleRepository interviewScheduleRepository;
    private final InterviewPanelRepository interviewPanelRepository;
    private final boolean calendarRequired;

    public CalendarSyncService(
            GoogleCalendarEventService eventService,
            GoogleCalendarTokenService tokenService,
            UserSettingsService userSettingsService,
            AvailabilitySlotRepository availabilitySlotRepository,
            InterviewScheduleRepository interviewScheduleRepository,
            InterviewPanelRepository interviewPanelRepository,
            @Value("${google.calendar.required:false}") boolean calendarRequired) {
        this.eventService = eventService;
        this.tokenService = tokenService;
        this.userSettingsService = userSettingsService;
        this.availabilitySlotRepository = availabilitySlotRepository;
        this.interviewScheduleRepository = interviewScheduleRepository;
        this.interviewPanelRepository = interviewPanelRepository;
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

    @Transactional
    public void afterSingleInterviewBooked(
            AvailabilitySlot originalSlot,
            AvailabilitySlot bookedSlot,
            InterviewRequest request,
            InterviewSchedule schedule) {
        User interviewer = bookedSlot.getInterviewer();
        if (!tokenService.isConnected(interviewer)) {
            return;
        }

        try {
            String timeZone = resolveTimeZone(interviewer);
            boolean partialBooking = !originalSlot.getId().equals(bookedSlot.getId());

            if (partialBooking) {
                if (originalSlot.getGoogleCalendarEventId() != null) {
                    eventService.deleteEvent(interviewer, originalSlot.getGoogleCalendarEventId());
                    originalSlot.setGoogleCalendarEventId(null);
                    availabilitySlotRepository.save(originalSlot);
                }
                syncNewAvailabilityFragments(interviewer, originalSlot.getStartDateTime(), originalSlot.getEndDateTime());
            }

            List<String> attendees = buildInterviewAttendees(request);
            String title = "Interview: " + request.getCandidateName();
            var result = eventService.bookInterviewEvent(
                    interviewer,
                    partialBooking ? null : bookedSlot.getGoogleCalendarEventId(),
                    timeZone,
                    bookedSlot.getStartDateTime(),
                    bookedSlot.getEndDateTime(),
                    title,
                    attendees,
                    true);

            bookedSlot.setGoogleCalendarEventId(result.eventId());
            availabilitySlotRepository.save(bookedSlot);

            schedule.setGoogleCalendarEventId(result.eventId());
            schedule.setMeetingLink(result.meetingLink());
            interviewScheduleRepository.save(schedule);
        } catch (Exception e) {
            logger.warn("Failed to book Google Calendar interview for request {}: {}", request.getId(), e.getMessage());
        }
    }

    @Transactional
    public void cancelSingleInterview(
            InterviewRequest request,
            InterviewSchedule schedule,
            AvailabilitySlot restoredSlot) {
        User interviewer = restoredSlot.getInterviewer();
        if (!tokenService.isConnected(interviewer)) {
            return;
        }

        try {
            String eventId = schedule.getGoogleCalendarEventId() != null
                    ? schedule.getGoogleCalendarEventId()
                    : restoredSlot.getGoogleCalendarEventId();

            if (eventId == null) {
                syncAvailabilitySlotCreated(restoredSlot);
                return;
            }

            String timeZone = resolveTimeZone(interviewer);
            var result = eventService.revertToAvailabilityEvent(
                    interviewer,
                    eventId,
                    timeZone,
                    restoredSlot.getStartDateTime(),
                    restoredSlot.getEndDateTime(),
                    "Available for Interview");

            restoredSlot.setGoogleCalendarEventId(result.eventId());
            availabilitySlotRepository.save(restoredSlot);

            schedule.setGoogleCalendarEventId(result.eventId());
            schedule.setMeetingLink(null);
            interviewScheduleRepository.save(schedule);
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

        User leadInterviewer = bookedSlots.get(0).getInterviewer();
        if (!tokenService.isConnected(leadInterviewer)) {
            return;
        }

        try {
            for (AvailabilitySlot bookedSlot : bookedSlots) {
                User interviewer = bookedSlot.getInterviewer();
                if (bookedSlot.getGoogleCalendarEventId() != null && tokenService.isConnected(interviewer)) {
                    eventService.deleteEvent(interviewer, bookedSlot.getGoogleCalendarEventId());
                    bookedSlot.setGoogleCalendarEventId(null);
                    availabilitySlotRepository.save(bookedSlot);
                }
            }

            String timeZone = resolveTimeZone(leadInterviewer);
            List<String> attendees = buildPanelAttendees(requests, panel);
            String title = "Panel Interview: " + panel.getCandidateName();
            var result = eventService.createPanelInterviewEvent(
                    leadInterviewer,
                    timeZone,
                    panel.getStartDateTime(),
                    panel.getEndDateTime(),
                    title,
                    attendees);

            panel.setGoogleCalendarEventId(result.eventId());
            panel.setMeetingLink(result.meetingLink());
            interviewPanelRepository.save(panel);

            for (InterviewRequest request : requests) {
                interviewScheduleRepository.findByRequestId(request.getId()).ifPresent(schedule -> {
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
        } catch (Exception e) {
            logger.warn("Failed to book Google Calendar panel {}: {}", panel.getId(), e.getMessage());
        }
    }

    @Transactional
    public void cancelPanelInterview(
            InterviewPanel panel,
            List<InterviewRequest> requests,
            List<AvailabilitySlot> restoredSlots) {
        if (panel.getGoogleCalendarEventId() != null && !restoredSlots.isEmpty()) {
            User leadInterviewer = restoredSlots.get(0).getInterviewer();
            if (tokenService.isConnected(leadInterviewer)) {
                try {
                    eventService.deleteEvent(leadInterviewer, panel.getGoogleCalendarEventId());
                } catch (Exception e) {
                    logger.warn("Failed to delete panel Google event {}: {}", panel.getId(), e.getMessage());
                }
            }
        }

        panel.setGoogleCalendarEventId(null);
        panel.setMeetingLink(null);
        interviewPanelRepository.save(panel);

        for (AvailabilitySlot restoredSlot : restoredSlots) {
            restoredSlot.setGoogleCalendarEventId(null);
            availabilitySlotRepository.save(restoredSlot);
            syncAvailabilitySlotCreated(restoredSlot);
        }

        for (InterviewRequest request : requests) {
            interviewScheduleRepository.findByRequestId(request.getId()).ifPresent(schedule -> {
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

    private List<String> buildInterviewAttendees(InterviewRequest request) {
        Set<String> emails = new LinkedHashSet<>();
        if (request.getCandidate() != null && request.getCandidate().getEmail() != null) {
            emails.add(request.getCandidate().getEmail());
        }
        if (request.getInterviewCoordinator() != null && request.getInterviewCoordinator().getEmail() != null) {
            emails.add(request.getInterviewCoordinator().getEmail());
        }
        if (request.getCandidate() != null
                && request.getCandidate().getCoordinatedHr() != null
                && request.getCandidate().getCoordinatedHr().getEmail() != null) {
            emails.add(request.getCandidate().getCoordinatedHr().getEmail());
        }
        return new ArrayList<>(emails);
    }

    private List<String> buildPanelAttendees(List<InterviewRequest> requests, InterviewPanel panel) {
        Set<String> emails = new LinkedHashSet<>();
        for (InterviewRequest request : requests) {
            if (request.getAssignedInterviewer() != null && request.getAssignedInterviewer().getEmail() != null) {
                emails.add(request.getAssignedInterviewer().getEmail());
            }
        }
        if (panel.getCandidate() != null && panel.getCandidate().getEmail() != null) {
            emails.add(panel.getCandidate().getEmail());
        }
        if (panel.getInterviewCoordinator() != null && panel.getInterviewCoordinator().getEmail() != null) {
            emails.add(panel.getInterviewCoordinator().getEmail());
        }
        if (panel.getCandidate() != null
                && panel.getCandidate().getCoordinatedHr() != null
                && panel.getCandidate().getCoordinatedHr().getEmail() != null) {
            emails.add(panel.getCandidate().getCoordinatedHr().getEmail());
        }
        return new ArrayList<>(emails);
    }

    private String resolveTimeZone(User user) {
        return userSettingsService.resolveTimezoneForCalendarSync(user);
    }
}
