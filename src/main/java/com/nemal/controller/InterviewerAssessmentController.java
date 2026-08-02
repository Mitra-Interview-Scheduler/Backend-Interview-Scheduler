package com.nemal.controller;

import com.nemal.entity.User;
import com.nemal.service.AssessmentService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/interviewer/assessments")
@CrossOrigin(origins = "http://localhost:5173")
public class InterviewerAssessmentController {

    private static final Logger logger = LoggerFactory.getLogger(InterviewerAssessmentController.class);
    private final AssessmentService assessmentService;

    public InterviewerAssessmentController(AssessmentService assessmentService) {
        this.assessmentService = assessmentService;
    }

    @GetMapping
    public ResponseEntity<?> listMine(@AuthenticationPrincipal User user) {
        try {
            return ResponseEntity.ok(assessmentService.listAssignedAssessments(user));
        } catch (Exception e) {
            logger.warn("Failed to list assessments for user {}: {}", user != null ? user.getId() : null, e.getMessage());
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        }
    }

    @GetMapping("/{scheduleId}/download")
    public ResponseEntity<?> download(
            @AuthenticationPrincipal User user,
            @PathVariable Long scheduleId) {
        try {
            ResponseEntity<Resource> response = assessmentService.downloadAssessmentFile(user, scheduleId);
            return response;
        } catch (Exception e) {
            logger.warn("Failed to download assessment {}: {}", scheduleId, e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("message", e.getMessage()));
        }
    }

    @GetMapping("/{scheduleId}")
    public ResponseEntity<?> getOne(
            @AuthenticationPrincipal User user,
            @PathVariable Long scheduleId) {
        try {
            // Access check: must be assigned reviewer (download path enforces; list/detail for assigned)
            boolean assigned = assessmentService.listAssignedAssessments(user).stream()
                    .anyMatch(a -> scheduleId.equals(a.interviewScheduleId()));
            if (!assigned) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN)
                        .body(Map.of("message", "You are not assigned to this assessment"));
            }
            return ResponseEntity.ok(assessmentService.getAssessment(scheduleId));
        } catch (Exception e) {
            logger.warn("Failed to load assessment {}: {}", scheduleId, e.getMessage());
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        }
    }

    @PostMapping("/{scheduleId}/complete")
    public ResponseEntity<?> complete(
            @AuthenticationPrincipal User user,
            @PathVariable Long scheduleId) {
        try {
            return ResponseEntity.ok(assessmentService.markCompletedByReviewer(user, scheduleId));
        } catch (Exception e) {
            logger.warn("Failed to complete assessment {}: {}", scheduleId, e.getMessage());
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        }
    }
}
