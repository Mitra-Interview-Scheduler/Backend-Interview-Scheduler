package com.nemal.controller;

import com.nemal.dto.GoogleCalendarAvailabilitySyncDto;
import com.nemal.dto.GoogleCalendarConnectDto;
import com.nemal.dto.GoogleCalendarExternalEventDto;
import com.nemal.dto.GoogleCalendarStatusDto;
import com.nemal.entity.User;
import com.nemal.service.CalendarSyncService;
import com.nemal.service.GoogleCalendarOAuthService;
import com.nemal.service.GoogleCalendarTokenService;
import com.nemal.util.TimeZoneMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.view.RedirectView;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;

@RestController
@RequestMapping("/api/integrations/google-calendar")
public class GoogleCalendarIntegrationController {

    private final GoogleCalendarOAuthService oauthService;
    private final GoogleCalendarTokenService tokenService;
    private final CalendarSyncService calendarSyncService;
    private final boolean calendarRequired;

    public GoogleCalendarIntegrationController(
            GoogleCalendarOAuthService oauthService,
            GoogleCalendarTokenService tokenService,
            CalendarSyncService calendarSyncService,
            @Value("${google.calendar.required:false}") boolean calendarRequired) {
        this.oauthService = oauthService;
        this.tokenService = tokenService;
        this.calendarSyncService = calendarSyncService;
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
                        true))
                .toList();
        return ResponseEntity.ok(events);
    }

    @DeleteMapping
    public ResponseEntity<Void> disconnect(@AuthenticationPrincipal User user) {
        if (calendarRequired && user.hasInterviewerRole()) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Google Calendar is required for interviewer accounts and cannot be disconnected.");
        }
        tokenService.revokeToken(user);
        tokenService.deleteCredentials(user);
        return ResponseEntity.noContent().build();
    }
}
