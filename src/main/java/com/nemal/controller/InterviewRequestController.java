package com.nemal.controller;

import com.nemal.dto.ConflictCheckRequestDto;
import com.nemal.dto.CreateInterviewRequestDto;
import com.nemal.dto.GoogleCalendarExternalEventDto;
import com.nemal.dto.InterviewRequestDto;
import com.nemal.dto.InterviewerConflictsDto;
import com.nemal.entity.User;
import com.nemal.service.InterviewRequestService;
import com.nemal.util.TimeZoneMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/hr/interviews")
@CrossOrigin(origins = "http://localhost:5173")
public class InterviewRequestController {

    private static final Logger logger = LoggerFactory.getLogger(InterviewRequestController.class);
    private final InterviewRequestService interviewRequestService;

    public InterviewRequestController(InterviewRequestService interviewRequestService) {
        this.interviewRequestService = interviewRequestService;
    }

    /**
     * HR schedules a single-interviewer interview.
     * Supports partial slot booking (slot splitting).
     */
    @PostMapping
    public ResponseEntity<?> createInterviewRequest(
            @AuthenticationPrincipal User user,
            @RequestBody CreateInterviewRequestDto dto,
            @RequestHeader(value = "X-Timezone", required = false) String timezone) {
        try {
            ZoneId zone = TimeZoneMapper.resolveZone(timezone);
            InterviewRequestDto result = interviewRequestService.createInterviewRequest(user, TimeZoneMapper.toUtc(dto, zone));
            return ResponseEntity.status(HttpStatus.CREATED).body(TimeZoneMapper.fromUtc(result, zone));
        } catch (Exception e) {
            logger.error("Failed to create interview request: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of("message", e.getMessage()));
        }
    }

    /**
     * Warn-before-scheduling: returns the selected interviewer(s)' Google Calendar
     * events that overlap the proposed interview window. An empty list means no
     * conflicts. HR may still schedule over a conflict (this is advisory only).
     */
    @PostMapping("/conflict-check")
    public ResponseEntity<?> checkConflicts(
            @AuthenticationPrincipal User user,
            @RequestBody ConflictCheckRequestDto dto,
            @RequestHeader(value = "X-Timezone", required = false) String timezone) {
        try {
            ZoneId zone = TimeZoneMapper.resolveZone(timezone);
            LocalDateTime utcStart = TimeZoneMapper.toUtc(dto.startDateTime(), zone);
            LocalDateTime utcEnd = TimeZoneMapper.toUtc(dto.endDateTime(), zone);

            List<InterviewerConflictsDto> conflicts = interviewRequestService
                    .findSchedulingConflicts(dto.interviewerIds(), utcStart, utcEnd)
                    .stream()
                    .map(ic -> new InterviewerConflictsDto(
                            ic.interviewerId(),
                            ic.interviewerName(),
                            ic.conflicts().stream()
                                    .map(event -> new GoogleCalendarExternalEventDto(
                                            event.googleEventId(),
                                            event.title(),
                                            TimeZoneMapper.fromUtc(event.startDateTime(), zone),
                                            TimeZoneMapper.fromUtc(event.endDateTime(), zone),
                                            event.allDay(),
                                            event.readOnly(),
                                            event.calendarName()))
                                    .toList()))
                    .toList();

            return ResponseEntity.ok(conflicts);
        } catch (Exception e) {
            logger.error("Failed to check scheduling conflicts: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of("message", e.getMessage()));
        }
    }

    @GetMapping("/my-requests")
    public ResponseEntity<?> getMyRequests(
            @AuthenticationPrincipal User user,
            @RequestParam(required = false) Integer size,
            @RequestParam(required = false) Long departmentId,
            @RequestParam(required = false) Integer minTierId,
            @RequestParam(required = false) Integer exactTierId,
            @RequestHeader(value = "X-Timezone", required = false) String timezone) {
        try {
            ZoneId zone = TimeZoneMapper.resolveZone(timezone);
            List<InterviewRequestDto> result = size != null
                    ? interviewRequestService.getRequestsByUser(user.getId(), size, departmentId, minTierId, exactTierId)
                    : interviewRequestService.getRequestsByUser(user.getId(), departmentId, minTierId, exactTierId);
            return ResponseEntity.ok(TimeZoneMapper.fromUtcInterviewRequests(result, zone));
        } catch (Exception e) {
            logger.error("Failed to get requests for user {}: {}", user.getId(), e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("message", e.getMessage()));
        }
    }

    @GetMapping("/candidate/{candidateId}")
    public ResponseEntity<?> getRequestsByCandidate(
            @PathVariable Long candidateId,
            @RequestHeader(value = "X-Timezone", required = false) String timezone) {
        try {
            ZoneId zone = TimeZoneMapper.resolveZone(timezone);
            List<InterviewRequestDto> result = interviewRequestService.getRequestsByCandidate(candidateId);
            return ResponseEntity.ok(TimeZoneMapper.fromUtcInterviewRequests(result, zone));
        } catch (Exception e) {
            logger.error("Failed to get requests for candidate {}: {}", candidateId, e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("message", e.getMessage()));
        }
    }

    @DeleteMapping("/{requestId}")
    public ResponseEntity<?> cancelRequest(
            @AuthenticationPrincipal User user,
            @PathVariable Long requestId) {
        try {
            interviewRequestService.cancelRequest(user, requestId);
            return ResponseEntity.noContent().build();
        } catch (Exception e) {
            logger.error("Failed to cancel request {}: {}", requestId, e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of("message", e.getMessage()));
        }
    }

    @PatchMapping("/schedules/{scheduleId}/complete")
    public ResponseEntity<?> completeInterview(
            @AuthenticationPrincipal User user,
            @PathVariable Long scheduleId,
            @RequestHeader(value = "X-Timezone", required = false) String timezone) {
        try {
            ZoneId zone = TimeZoneMapper.resolveZone(timezone);
            InterviewRequestDto result = interviewRequestService.completeInterview(user, scheduleId);
            return ResponseEntity.ok(TimeZoneMapper.fromUtc(result, zone));
        } catch (Exception e) {
            logger.error("Failed to complete interview schedule {}: {}", scheduleId, e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of("message", e.getMessage()));
        }
    }
}