package com.nemal.controller;

import com.nemal.dto.ApproveInterviewPostponeRequestDto;
import com.nemal.dto.InterviewPostponeRequestDto;
import com.nemal.dto.RejectInterviewPostponeRequestDto;
import com.nemal.entity.User;
import com.nemal.service.InterviewPostponeRequestService;
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
@RequestMapping("/api/hr/interviews/postpone-requests")
@CrossOrigin(origins = "http://localhost:5173")
public class InterviewPostponeRequestController {

    private static final Logger logger = LoggerFactory.getLogger(InterviewPostponeRequestController.class);

    private final InterviewPostponeRequestService postponeRequestService;

    public InterviewPostponeRequestController(InterviewPostponeRequestService postponeRequestService) {
        this.postponeRequestService = postponeRequestService;
    }

    @GetMapping("/pending-count")
    public ResponseEntity<?> pendingCount() {
        return ResponseEntity.ok(Map.of("count", postponeRequestService.countPendingRequests()));
    }

    @GetMapping
    public ResponseEntity<?> listPending(
            @RequestHeader(value = "X-Timezone", required = false) String timezone) {
        try {
            ZoneId zone = TimeZoneMapper.resolveZone(timezone);
            List<InterviewPostponeRequestDto> items = postponeRequestService.listPendingRequests()
                    .stream()
                    .map(dto -> TimeZoneMapper.fromUtc(dto, zone))
                    .toList();
            return ResponseEntity.ok(items);
        } catch (Exception e) {
            logger.error("Failed to list pending postpone requests: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("message", e.getMessage()));
        }
    }

    @GetMapping("/schedules/{scheduleId}")
    public ResponseEntity<?> getPendingForSchedule(
            @PathVariable Long scheduleId,
            @RequestHeader(value = "X-Timezone", required = false) String timezone) {
        try {
            ZoneId zone = TimeZoneMapper.resolveZone(timezone);
            InterviewPostponeRequestDto pending = postponeRequestService.getPendingForSchedule(scheduleId);
            if (pending == null) {
                return ResponseEntity.ok(Map.of("pending", false));
            }
            return ResponseEntity.ok(Map.of(
                    "pending", true,
                    "request", TimeZoneMapper.fromUtc(pending, zone)
            ));
        } catch (Exception e) {
            logger.error("Failed to get postpone request for schedule {}: {}", scheduleId, e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of("message", e.getMessage()));
        }
    }

    @GetMapping("/schedules/{scheduleId}/history")
    public ResponseEntity<?> getHistoryForSchedule(
            @PathVariable Long scheduleId,
            @RequestHeader(value = "X-Timezone", required = false) String timezone) {
        try {
            ZoneId zone = TimeZoneMapper.resolveZone(timezone);
            List<InterviewPostponeRequestDto> history = postponeRequestService.getHistoryForSchedule(scheduleId)
                    .stream()
                    .map(dto -> TimeZoneMapper.fromUtc(dto, zone))
                    .toList();
            return ResponseEntity.ok(history);
        } catch (Exception e) {
            logger.error("Failed to get postpone history for schedule {}: {}", scheduleId, e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of("message", e.getMessage()));
        }
    }

    @PatchMapping("/{postponeRequestId}/reject")
    public ResponseEntity<?> reject(
            @AuthenticationPrincipal User user,
            @PathVariable Long postponeRequestId,
            @RequestBody(required = false) RejectInterviewPostponeRequestDto dto,
            @RequestHeader(value = "X-Timezone", required = false) String timezone) {
        try {
            ZoneId zone = TimeZoneMapper.resolveZone(timezone);
            InterviewPostponeRequestDto result = postponeRequestService.rejectPostponeRequest(
                    user, postponeRequestId, dto);
            return ResponseEntity.ok(TimeZoneMapper.fromUtc(result, zone));
        } catch (Exception e) {
            logger.error("Failed to reject postpone request {}: {}", postponeRequestId, e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of("message", e.getMessage()));
        }
    }

    @PatchMapping("/{postponeRequestId}/approve")
    public ResponseEntity<?> approve(
            @AuthenticationPrincipal User user,
            @PathVariable Long postponeRequestId,
            @RequestBody(required = false) ApproveInterviewPostponeRequestDto dto,
            @RequestHeader(value = "X-Timezone", required = false) String timezone) {
        try {
            ZoneId zone = TimeZoneMapper.resolveZone(timezone);
            InterviewPostponeRequestDto result = postponeRequestService.approvePostponeRequest(
                    user, postponeRequestId, dto);
            return ResponseEntity.ok(TimeZoneMapper.fromUtc(result, zone));
        } catch (Exception e) {
            logger.error("Failed to approve postpone request {}: {}", postponeRequestId, e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of("message", e.getMessage()));
        }
    }
}
