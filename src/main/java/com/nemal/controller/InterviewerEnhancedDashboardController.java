package com.nemal.controller;

import com.nemal.dto.dashboard.InterviewerDashboardResponse;
import com.nemal.entity.User;
import com.nemal.service.dashboard.InterviewerDashboardService;
import com.nemal.util.TimeZoneMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.time.ZoneId;
import java.util.Map;

/**
 * Enhanced interviewer dashboard aggregations.
 * Date/time fields are converted via {@code X-Timezone} (UTC when omitted).
 */
@RestController
@RequestMapping("/api/interviewer/dashboard")
@CrossOrigin(origins = "http://localhost:5173")
public class InterviewerEnhancedDashboardController {

    private static final Logger logger = LoggerFactory.getLogger(InterviewerEnhancedDashboardController.class);

    private final InterviewerDashboardService interviewerDashboardService;

    public InterviewerEnhancedDashboardController(InterviewerDashboardService interviewerDashboardService) {
        this.interviewerDashboardService = interviewerDashboardService;
    }

    @GetMapping
    public ResponseEntity<?> getDashboard(
            @AuthenticationPrincipal User user,
            @RequestParam(defaultValue = "5") int previewSize,
            @RequestParam(defaultValue = "7") int upcomingDays,
            @RequestParam(defaultValue = "14") int coverageDays,
            @RequestHeader(value = "X-Timezone", required = false) String timezone
    ) {
        try {
            ZoneId zone = TimeZoneMapper.resolveZone(timezone);
            InterviewerDashboardResponse response =
                    interviewerDashboardService.getDashboard(user, previewSize, upcomingDays, coverageDays, zone);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.error("Failed to build interviewer dashboard for {}: {}",
                    user != null ? user.getId() : null, e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("message", e.getMessage() != null ? e.getMessage() : "Failed to load dashboard"));
        }
    }

    @GetMapping("/items")
    public ResponseEntity<?> getDashboardItems(
            @AuthenticationPrincipal User user,
            @RequestParam String section,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(defaultValue = "7") int upcomingDays,
            @RequestHeader(value = "X-Timezone", required = false) String timezone
    ) {
        try {
            ZoneId zone = TimeZoneMapper.resolveZone(timezone);
            return ResponseEntity.ok(interviewerDashboardService.getDashboardItems(
                    user, section, page, size, upcomingDays, zone));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of("message", e.getMessage()));
        } catch (Exception e) {
            logger.error("Failed to load interviewer dashboard section {}: {}", section, e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("message", e.getMessage() != null ? e.getMessage() : "Failed to load dashboard items"));
        }
    }

    @GetMapping("/coverage")
    public ResponseEntity<?> getAvailabilityCoverage(
            @AuthenticationPrincipal User user,
            @RequestParam(defaultValue = "14") int coverageDays,
            @RequestHeader(value = "X-Timezone", required = false) String timezone
    ) {
        try {
            ZoneId zone = TimeZoneMapper.resolveZone(timezone);
            return ResponseEntity.ok(interviewerDashboardService.getAvailabilityCoverage(user, coverageDays, zone));
        } catch (Exception e) {
            logger.error("Failed to load interviewer coverage for {}: {}",
                    user != null ? user.getId() : null, e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("message", e.getMessage() != null ? e.getMessage() : "Failed to load coverage"));
        }
    }
}
