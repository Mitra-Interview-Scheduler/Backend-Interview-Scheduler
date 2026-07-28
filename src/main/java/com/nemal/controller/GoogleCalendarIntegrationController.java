package com.nemal.controller;

import com.nemal.dto.GoogleCalendarAvailabilitySyncDto;
import com.nemal.dto.GoogleCalendarConnectDto;
import com.nemal.dto.GoogleCalendarExternalEventDto;
import com.nemal.dto.GoogleCalendarListItemDto;
import com.nemal.dto.GoogleCalendarSelectionRequestDto;
import com.nemal.dto.GoogleCalendarSelectionResponseDto;
import com.nemal.dto.GoogleCalendarStatusDto;
import com.nemal.entity.User;
import com.nemal.service.CalendarSyncService;
import com.nemal.service.GoogleCalendarEventService;
import com.nemal.service.GoogleCalendarOAuthService;
import com.nemal.service.GoogleCalendarTokenService;
import com.nemal.util.TimeZoneMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.view.RedirectView;

import java.io.IOException;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;

@RestController
@RequestMapping("/api/integrations/google-calendar")
public class GoogleCalendarIntegrationController {

    private static final Logger logger = LoggerFactory.getLogger(GoogleCalendarIntegrationController.class);

    private final GoogleCalendarOAuthService oauthService;
    private final GoogleCalendarTokenService tokenService;
    private final CalendarSyncService calendarSyncService;
    private final GoogleCalendarEventService eventService;
    private final boolean calendarRequired;

    public GoogleCalendarIntegrationController(
            GoogleCalendarOAuthService oauthService,
            GoogleCalendarTokenService tokenService,
            CalendarSyncService calendarSyncService,
            GoogleCalendarEventService eventService,
            @Value("${google.calendar.required:false}") boolean calendarRequired) {
        this.oauthService = oauthService;
        this.tokenService = tokenService;
        this.calendarSyncService = calendarSyncService;
        this.eventService = eventService;
        this.calendarRequired = calendarRequired;
    }

    @GetMapping("/connect")
    public ResponseEntity<GoogleCalendarConnectDto> connect(
            @AuthenticationPrincipal User user,
            @RequestParam(required = false) String returnTo) {
        String authorizationUrl = oauthService.buildAuthorizationUrl(user, returnTo);
        return ResponseEntity.ok(new GoogleCalendarConnectDto(authorizationUrl));
    }

    @GetMapping("/callback")
    public RedirectView callback(
            @RequestParam(required = false) String code,
            @RequestParam(required = false) String state,
            @RequestParam(required = false) String error) {
        if (error != null) {
            return new RedirectView(oauthService.buildFailureRedirectUrl(error));
        }
        if (code == null || state == null) {
            return new RedirectView(oauthService.buildFailureRedirectUrl("missing_code_or_state"));
        }
        try {
            return new RedirectView(oauthService.handleCallback(code, state));
        } catch (Exception e) {
            return new RedirectView(oauthService.buildFailureRedirectUrl(e.getMessage()));
        }
    }

    @GetMapping("/status")
    public ResponseEntity<GoogleCalendarStatusDto> status(@AuthenticationPrincipal User user) {
        boolean required = calendarRequired && user.hasInterviewerRole();
        return tokenService.findCredentials(user)
                .map(credentials -> ResponseEntity.ok(
                        new GoogleCalendarStatusDto(true, credentials.getGoogleAccountEmail(), required)))
                .orElseGet(() -> ResponseEntity.ok(new GoogleCalendarStatusDto(false, null, required)));
    }

    @PostMapping("/sync-availability")
    public ResponseEntity<GoogleCalendarAvailabilitySyncDto> syncAvailability(@AuthenticationPrincipal User user) {
        if (!user.hasInterviewerRole()) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Only interviewer accounts can sync availability to Google Calendar.");
        }
        if (!tokenService.isConnected(user)) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Google Calendar is not connected.");
        }
        return ResponseEntity.ok(calendarSyncService.syncUnsyncedAvailabilitySlotsResult(user));
    }

    @GetMapping("/external-events")
    public ResponseEntity<List<GoogleCalendarExternalEventDto>> externalEvents(
            @AuthenticationPrincipal User user,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime start,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime end,
            @RequestHeader(value = "X-Timezone", required = false) String timezone) {
        if (!user.hasInterviewerRole()) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Only interviewer accounts can view Google Calendar events.");
        }
        if (!tokenService.isConnected(user)) {
            return ResponseEntity.ok(List.of());
        }

        ZoneId zone = TimeZoneMapper.resolveZone(timezone);
        LocalDateTime utcStart = TimeZoneMapper.toUtc(start, zone);
        LocalDateTime utcEnd = TimeZoneMapper.toUtc(end, zone);
        List<GoogleCalendarExternalEventDto> events = calendarSyncService
                .listExternalGoogleCalendarEvents(user, utcStart, utcEnd)
                .stream()
                .map(event -> new GoogleCalendarExternalEventDto(
                        event.googleEventId(),
                        event.title(),
                        TimeZoneMapper.fromUtc(event.startDateTime(), zone),
                        TimeZoneMapper.fromUtc(event.endDateTime(), zone),
                        event.allDay(),
                        true,
                        event.calendarName()))
                .toList();
        return ResponseEntity.ok(events);
    }

    @GetMapping("/calendars")
    public ResponseEntity<List<GoogleCalendarListItemDto>> listCalendars(@AuthenticationPrincipal User user) {
        requireInterviewerWithCalendar(user);
        try {
            tokenService.validateStoredTokens(user);
            return ResponseEntity.ok(eventService.listCalendarsForUser(user));
        } catch (IllegalStateException e) {
            logger.warn("Invalid Google Calendar tokens for user {}: {}", user.getId(), e.getMessage());
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Google Calendar connection is invalid. Disconnect and reconnect from Settings.");
        } catch (Exception e) {
            logger.error("Failed to list Google calendars for user {}", user.getId(), e);
            throw new ResponseStatusException(
                    HttpStatus.BAD_GATEWAY,
                    mapCalendarListError(e));
        }
    }

    @PutMapping("/calendars/selection")
    public ResponseEntity<GoogleCalendarSelectionResponseDto> saveCalendarSelection(
            @AuthenticationPrincipal User user,
            @RequestBody GoogleCalendarSelectionRequestDto request) {
        requireInterviewerWithCalendar(user);
        List<String> saved = tokenService.saveSelectedCalendarIds(
                user,
                request != null ? request.calendarIds() : List.of());
        return ResponseEntity.ok(new GoogleCalendarSelectionResponseDto(saved, true));
    }

    @DeleteMapping
    public ResponseEntity<Void> disconnect(@AuthenticationPrincipal User user) {
        tokenService.revokeToken(user);
        tokenService.deleteCredentials(user);
        return ResponseEntity.noContent().build();
    }

    private void requireInterviewerWithCalendar(User user) {
        if (user == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Authentication required.");
        }
        if (!user.hasInterviewerRole()) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Only interviewer accounts can manage Google calendars.");
        }
        if (!tokenService.isConnected(user)) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Google Calendar is not connected.");
        }
    }

    private String mapCalendarListError(Exception e) {
        String message = e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
        String lower = message.toLowerCase();
        if (lower.contains("invalid_grant") || lower.contains("token has been expired or revoked")) {
            return "Google Calendar authorization expired. Disconnect and reconnect from Settings.";
        }
        if (lower.contains("insufficient") && lower.contains("permission")) {
            return "Google Calendar permission is missing. Disconnect and reconnect to grant calendar access.";
        }
        if (e instanceof IOException || lower.contains("401") || lower.contains("403")) {
            return "Google Calendar access was denied. Disconnect and reconnect from Settings.";
        }
        return "Failed to list Google calendars: " + message;
    }
}
