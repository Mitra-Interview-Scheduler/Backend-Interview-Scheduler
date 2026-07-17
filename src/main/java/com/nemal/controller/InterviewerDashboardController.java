package com.nemal.controller;

import com.nemal.dto.InterviewRequestDto;
import com.nemal.entity.User;
import com.nemal.service.InterviewRequestService;
import com.nemal.util.TimeZoneMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.time.ZoneId;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/interview-requests")
@CrossOrigin(origins = "http://localhost:5173")
public class InterviewerDashboardController {

    private static final Logger logger = LoggerFactory.getLogger(InterviewerDashboardController.class);
    private final InterviewRequestService interviewRequestService;

    public InterviewerDashboardController(InterviewRequestService interviewRequestService) {
        this.interviewRequestService = interviewRequestService;
    }

    @GetMapping("/upcoming")
    public ResponseEntity<?> getUpcomingInterviews(
            @AuthenticationPrincipal User user,
            @RequestParam(required = false) Integer size,
            @RequestHeader(value = "X-Timezone", required = false) String timezone) {
        try {
            ZoneId zone = TimeZoneMapper.resolveZone(timezone);
            List<InterviewRequestDto> result = size != null
                    ? interviewRequestService.getUpcomingInterviewsForInterviewer(user.getId(), size)
                    : interviewRequestService.getUpcomingInterviewsForInterviewer(user.getId());
            return ResponseEntity.ok(TimeZoneMapper.fromUtcInterviewRequests(result, zone));
        } catch (Exception e) {
            logger.error("Failed to get upcoming interviews for interviewer {}: {}", user.getId(), e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("message", e.getMessage()));
        }
    }

    @GetMapping("/my-interviews")
    public ResponseEntity<?> getMyInterviews(
            @AuthenticationPrincipal User user,
            @RequestHeader(value = "X-Timezone", required = false) String timezone) {
        try {
            ZoneId zone = TimeZoneMapper.resolveZone(timezone);
            List<InterviewRequestDto> result = interviewRequestService.getInterviewsForInterviewer(user.getId());
            return ResponseEntity.ok(TimeZoneMapper.fromUtcInterviewRequests(result, zone));
        } catch (Exception e) {
            logger.error("Failed to get interviews for interviewer {}: {}", user.getId(), e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("message", e.getMessage()));
        }
    }

    @PatchMapping("/{requestId}/respond")
    public ResponseEntity<?> respondToRequest(
            @AuthenticationPrincipal User user,
            @PathVariable Long requestId,
            @RequestBody Map<String, String> body,
            @RequestHeader(value = "X-Timezone", required = false) String timezone) {
        try {
            String action = body.get("action"); // "ACCEPT" or "DECLINE"
            String notes = body.get("notes");
            ZoneId zone = TimeZoneMapper.resolveZone(timezone);
            InterviewRequestDto result = interviewRequestService.respondToRequest(user, requestId, action, notes);
            return ResponseEntity.ok(TimeZoneMapper.fromUtc(result, zone));
        } catch (Exception e) {
            logger.error("Failed to respond to request {}: {}", requestId, e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of("message", e.getMessage()));
        }
    }
}