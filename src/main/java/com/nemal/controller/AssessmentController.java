package com.nemal.controller;

import com.nemal.dto.AssessmentReviewerDto;
import com.nemal.dto.AssessmentScheduleDto;
import com.nemal.dto.AssignAssessmentReviewersDto;
import com.nemal.entity.User;
import com.nemal.service.AssessmentService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/hr/assessments")
@CrossOrigin(origins = "http://localhost:5173")
public class AssessmentController {

    private static final Logger logger = LoggerFactory.getLogger(AssessmentController.class);
    private final AssessmentService assessmentService;

    public AssessmentController(AssessmentService assessmentService) {
        this.assessmentService = assessmentService;
    }

    @GetMapping("/{scheduleId}")
    public ResponseEntity<?> getAssessment(@PathVariable Long scheduleId) {
        try {
            return ResponseEntity.ok(assessmentService.getAssessment(scheduleId));
        } catch (Exception e) {
            logger.warn("Failed to load assessment {}: {}", scheduleId, e.getMessage());
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        }
    }

    @PostMapping(value = "/{scheduleId}/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<?> upload(
            @AuthenticationPrincipal User user,
            @PathVariable Long scheduleId,
            @RequestParam("file") MultipartFile file) {
        try {
            return ResponseEntity.ok(assessmentService.uploadAssessmentFile(user, scheduleId, file));
        } catch (Exception e) {
            logger.warn("Failed to upload assessment {}: {}", scheduleId, e.getMessage());
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        }
    }

    @GetMapping("/{scheduleId}/download")
    public ResponseEntity<?> download(
            @AuthenticationPrincipal User user,
            @PathVariable Long scheduleId) {
        try {
            return assessmentService.downloadAssessmentFile(user, scheduleId);
        } catch (Exception e) {
            logger.warn("Failed to download assessment {}: {}", scheduleId, e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("message", e.getMessage()));
        }
    }

    @PostMapping("/{scheduleId}/mark-received")
    public ResponseEntity<?> markReceived(
            @AuthenticationPrincipal User user,
            @PathVariable Long scheduleId) {
        try {
            return ResponseEntity.ok(assessmentService.markReceived(user, scheduleId));
        } catch (Exception e) {
            logger.warn("Failed to mark assessment {} received: {}", scheduleId, e.getMessage());
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        }
    }

    @PostMapping("/{scheduleId}/reviewers")
    public ResponseEntity<?> assignReviewers(
            @AuthenticationPrincipal User user,
            @PathVariable Long scheduleId,
            @RequestBody AssignAssessmentReviewersDto dto) {
        try {
            return ResponseEntity.ok(assessmentService.assignReviewers(user, scheduleId, dto));
        } catch (Exception e) {
            logger.warn("Failed to assign reviewers for assessment {}: {}", scheduleId, e.getMessage());
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        }
    }

    @GetMapping("/{scheduleId}/reviewers")
    public ResponseEntity<?> listReviewers(@PathVariable Long scheduleId) {
        try {
            List<AssessmentReviewerDto> reviewers = assessmentService.listReviewers(scheduleId);
            return ResponseEntity.ok(reviewers);
        } catch (Exception e) {
            logger.warn("Failed to list reviewers for assessment {}: {}", scheduleId, e.getMessage());
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        }
    }
}
