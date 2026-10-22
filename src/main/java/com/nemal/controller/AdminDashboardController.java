package com.nemal.controller;

import com.nemal.dto.dashboard.AdminDashboardResponse;
import com.nemal.entity.User;
import com.nemal.service.dashboard.AdminDashboardService;
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
 * Enhanced admin dashboard aggregations.
 * Date/time fields are converted via {@code X-Timezone} (UTC when omitted).
 */
@RestController
@RequestMapping("/api/admin/dashboard")
@CrossOrigin(origins = "http://localhost:5173")
public class AdminDashboardController {

    private static final Logger logger = LoggerFactory.getLogger(AdminDashboardController.class);

    private final AdminDashboardService adminDashboardService;

    public AdminDashboardController(AdminDashboardService adminDashboardService) {
        this.adminDashboardService = adminDashboardService;
    }

    @GetMapping
    public ResponseEntity<?> getDashboard(
            @AuthenticationPrincipal User user,
            @RequestParam(defaultValue = "30") int trendDays,
            @RequestParam(defaultValue = "5") int previewSize,
            @RequestHeader(value = "X-Timezone", required = false) String timezone
    ) {
        try {
            ZoneId zone = TimeZoneMapper.resolveZone(timezone);
            AdminDashboardResponse response = adminDashboardService.getDashboard(trendDays, previewSize, zone);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.error("Failed to build admin dashboard: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("message", e.getMessage() != null ? e.getMessage() : "Failed to load dashboard"));
        }
    }

    @GetMapping("/items")
    public ResponseEntity<?> getDashboardItems(
            @AuthenticationPrincipal User user,
            @RequestParam String section,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size
    ) {
        try {
            return ResponseEntity.ok(adminDashboardService.getDashboardItems(section, page, size));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of("message", e.getMessage()));
        } catch (Exception e) {
            logger.error("Failed to load admin dashboard section {}: {}", section, e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("message", e.getMessage() != null ? e.getMessage() : "Failed to load dashboard items"));
        }
    }
}
