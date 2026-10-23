package com.nemal.controller;

import com.nemal.exception.ApiErrors;
import com.nemal.dto.dashboard.DashboardActionResponse;
import com.nemal.dto.dashboard.HrDashboardResponse;
import com.nemal.entity.User;
import com.nemal.enums.Role;
import com.nemal.service.dashboard.HrDashboardService;
import com.nemal.util.TimeZoneMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.time.ZoneId;
import java.util.Map;

/**
 * Enhanced HR dashboard aggregations.
 * Date/time fields in the response are converted using the {@code X-Timezone} header
 * (UTC when omitted), matching other HR controllers.
 */
@RestController
@RequestMapping("/api/hr/dashboard")
@CrossOrigin(origins = "http://localhost:5173")
public class HrDashboardController {

    private static final Logger logger = LoggerFactory.getLogger(HrDashboardController.class);

    private final HrDashboardService hrDashboardService;

    public HrDashboardController(HrDashboardService hrDashboardService) {
        this.hrDashboardService = hrDashboardService;
    }

    @GetMapping
    public ResponseEntity<?> getDashboard(
            @AuthenticationPrincipal User user,
            @RequestParam(defaultValue = "false") boolean mineOnly,
            @RequestParam(defaultValue = "7") int stalledDays,
            @RequestParam(defaultValue = "24") int feedbackOverdueHours,
            @RequestParam(defaultValue = "14") int capacityDays,
            @RequestParam(defaultValue = "30") int trendDays,
            @RequestParam(defaultValue = "5") int previewSize,
            @RequestParam(defaultValue = "7") int upcomingDays,
            @RequestHeader(value = "X-Timezone", required = false) String timezone
    ) {
        try {
            ZoneId zone = TimeZoneMapper.resolveZone(timezone);
            // HR-only users always see their own work; All requires Admin as well.
            boolean scopedMineOnly = mineOnly || !user.hasRole(Role.ADMIN);
            HrDashboardResponse response = hrDashboardService.getDashboard(
                    user, scopedMineOnly, stalledDays, feedbackOverdueHours, capacityDays, trendDays,
                    previewSize, upcomingDays, zone);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.error("Failed to build HR dashboard: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("message", ApiErrors.safeMessage(e)));
        }
    }

    @GetMapping("/items")
    public ResponseEntity<?> getDashboardItems(
            @AuthenticationPrincipal User user,
            @RequestParam String section,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(defaultValue = "false") boolean mineOnly,
            @RequestParam(defaultValue = "7") int stalledDays,
            @RequestParam(defaultValue = "24") int feedbackOverdueHours,
            @RequestParam(defaultValue = "7") int upcomingDays,
            @RequestHeader(value = "X-Timezone", required = false) String timezone
    ) {
        try {
            ZoneId zone = TimeZoneMapper.resolveZone(timezone);
            boolean scopedMineOnly = mineOnly || !user.hasRole(Role.ADMIN);
            return ResponseEntity.ok(hrDashboardService.getDashboardItems(
                    user, section, page, size, scopedMineOnly, stalledDays, feedbackOverdueHours, upcomingDays, zone));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of("message", ApiErrors.safeMessage(e)));
        } catch (Exception e) {
            logger.error("Failed to load HR dashboard section {}: {}", section, e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("message", ApiErrors.safeMessage(e)));
        }
    }

    @PostMapping("/feedback-overdue/{scheduleId}/nudge")
    public ResponseEntity<?> nudgeFeedback(
            @AuthenticationPrincipal User user,
            @PathVariable Long scheduleId
    ) {
        try {
            DashboardActionResponse response = hrDashboardService.nudgeFeedbackOverdue(user, scheduleId);
            return ResponseEntity.ok(response);
        } catch (ResponseStatusException e) {
            return ResponseEntity.status(e.getStatusCode())
                    .body(Map.of("message", ApiErrors.safeMessage(e)));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of("message", ApiErrors.safeMessage(e)));
        } catch (Exception e) {
            logger.error("Failed to ping feedback for schedule {}: {}", scheduleId, e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("message", ApiErrors.safeMessage(e)));
        }
    }
}
