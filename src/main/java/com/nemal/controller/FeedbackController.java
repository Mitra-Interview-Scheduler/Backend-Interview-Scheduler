package com.nemal.controller;

import com.nemal.dto.CreateFeedbackResponseDto;
import com.nemal.dto.CreateFeedbackFormDto;
import com.nemal.dto.CreateFeedbackQuestionDto;
import com.nemal.dto.FeedbackFormDto;
import com.nemal.dto.FeedbackQuestionDto;
import com.nemal.dto.FeedbackResponseDto;
import com.nemal.entity.User;
import com.nemal.service.FeedbackService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
// HttpStatus and ResponseEntity already imported above
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import org.springframework.security.access.prepost.PreAuthorize;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

@RestController
@RequestMapping("/api/feedback")
@CrossOrigin(origins = "http://localhost:5173")
public class FeedbackController {

    private static final Logger logger = LoggerFactory.getLogger(FeedbackController.class);
    private final FeedbackService feedbackService;

    public FeedbackController(FeedbackService feedbackService) {
        this.feedbackService = feedbackService;
    }

    @GetMapping("/questions")
    public ResponseEntity<?> getFeedbackQuestions() {
        try {
            FeedbackFormDto form = feedbackService.getActiveFeedbackForm();
            return ResponseEntity.ok(form);
        } catch (Exception e) {
            logger.error("Failed to load feedback questions: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("message", e.getMessage()));
        }
    }

    @PreAuthorize("hasAnyRole('HR','ADMIN')")
    @PostMapping("/forms")
    public ResponseEntity<?> createForm(@Valid @RequestBody CreateFeedbackFormDto dto) {
        try {
            FeedbackFormDto created = feedbackService.createForm(dto);
            return ResponseEntity.status(HttpStatus.CREATED).body(created);
        } catch (Exception e) {
            logger.error("Failed to create feedback form: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("message", e.getMessage()));
        }
    }

    @PreAuthorize("hasAnyRole('HR','ADMIN')")
    @GetMapping("/forms")
    public ResponseEntity<?> listForms() {
        try {
            return ResponseEntity.ok(feedbackService.listAllForms());
        } catch (Exception e) {
            logger.error("Failed to list feedback forms: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of("message", e.getMessage()));
        }
    }

    @PreAuthorize("hasAnyRole('HR','ADMIN')")
    @GetMapping("/forms/{id}")
    public ResponseEntity<?> getForm(@PathVariable Long id) {
        try {
            return ResponseEntity.ok(feedbackService.getFormById(id));
        } catch (Exception e) {
            logger.error("Failed to get feedback form {}: {}", id, e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("message", e.getMessage()));
        }
    }

    @PreAuthorize("hasAnyRole('HR','ADMIN')")
    @PutMapping("/forms/{id}")
    public ResponseEntity<?> updateForm(@PathVariable Long id, @Valid @RequestBody CreateFeedbackFormDto dto) {
        try {
            FeedbackFormDto updated = feedbackService.updateForm(id, dto);
            return ResponseEntity.ok(updated);
        } catch (Exception e) {
            logger.error("Failed to update feedback form {}: {}", id, e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("message", e.getMessage()));
        }
    }

    @PreAuthorize("hasAnyRole('HR','ADMIN')")
    @DeleteMapping("/forms/{id}")
    public ResponseEntity<?> deleteForm(@PathVariable Long id) {
        try {
            feedbackService.deleteForm(id);
            return ResponseEntity.noContent().build();
        } catch (Exception e) {
            logger.error("Failed to delete feedback form {}: {}", id, e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("message", e.getMessage()));
        }
    }

    @PreAuthorize("hasAnyRole('HR','ADMIN')")
    @PatchMapping("/forms/{id}/status")
    public ResponseEntity<?> updateFormStatus(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        try {
            boolean active = body != null && Boolean.TRUE.equals(body.get("active"));
            FeedbackFormDto updated = feedbackService.setFormActive(id, active);
            return ResponseEntity.ok(updated);
        } catch (Exception e) {
            logger.error("Failed to update feedback form status {}: {}", id, e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("message", e.getMessage()));
        }
    }

    @PreAuthorize("hasAnyRole('HR','ADMIN')")
    @PostMapping("/forms/{formId}/questions")
    public ResponseEntity<?> createQuestion(@PathVariable Long formId, @Valid @RequestBody CreateFeedbackQuestionDto dto) {
        try {
            FeedbackQuestionDto created = feedbackService.createQuestion(formId, dto);
            return ResponseEntity.status(HttpStatus.CREATED).body(created);
        } catch (Exception e) {
            logger.error("Failed to create feedback question: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("message", e.getMessage()));
        }
    }

    @PreAuthorize("hasAnyRole('HR','ADMIN')")
    @PutMapping("/questions/{id}")
    public ResponseEntity<?> updateQuestion(@PathVariable Long id, @Valid @RequestBody CreateFeedbackQuestionDto dto) {
        try {
            FeedbackQuestionDto updated = feedbackService.updateQuestion(id, dto);
            return ResponseEntity.ok(updated);
        } catch (Exception e) {
            logger.error("Failed to update feedback question {}: {}", id, e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("message", e.getMessage()));
        }
    }

    @PreAuthorize("hasAnyRole('HR','ADMIN')")
    @DeleteMapping("/questions/{id}")
    public ResponseEntity<?> deleteQuestion(@PathVariable Long id) {
        try {
            feedbackService.deleteQuestion(id);
            return ResponseEntity.noContent().build();
        } catch (Exception e) {
            logger.error("Failed to delete feedback question {}: {}", id, e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("message", e.getMessage()));
        }
    }

    @PostMapping("/responses")
    public ResponseEntity<?> submitFeedback(
            @AuthenticationPrincipal User user,
            @RequestBody CreateFeedbackResponseDto dto) {
        try {
            FeedbackResponseDto response = feedbackService.submitFeedback(dto, user);
            return ResponseEntity.status(HttpStatus.CREATED).body(response);
        } catch (Exception e) {
            logger.error("Failed to submit feedback: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of("message", e.getMessage()));
        }
    }

    @GetMapping("/responses/{interviewScheduleId}")
    public ResponseEntity<?> getFeedbackByInterview(@PathVariable Long interviewScheduleId) {
        try {
            FeedbackResponseDto response = feedbackService.getFeedbackForInterview(interviewScheduleId);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.error("Failed to load feedback for interview {}: {}", interviewScheduleId, e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("message", e.getMessage()));
        }
    }
}
