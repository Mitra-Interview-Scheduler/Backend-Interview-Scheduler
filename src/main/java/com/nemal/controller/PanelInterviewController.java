package com.nemal.controller;

import com.nemal.dto.CreatePanelInterviewDto;
import com.nemal.dto.InterviewPanelDto;
import com.nemal.entity.User;
import com.nemal.service.PanelInterviewService;
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
@RequestMapping("/api/hr/panels")
@CrossOrigin(origins = "http://localhost:5173")
public class PanelInterviewController {

    private static final Logger logger = LoggerFactory.getLogger(PanelInterviewController.class);
    private final PanelInterviewService panelInterviewService;

    public PanelInterviewController(PanelInterviewService panelInterviewService) {
        this.panelInterviewService = panelInterviewService;
    }

    @PostMapping
    public ResponseEntity<?> createPanelInterview(
            @AuthenticationPrincipal User user,
            @RequestBody CreatePanelInterviewDto dto,
            @RequestHeader(value = "X-Timezone", required = false) String timezone) {
        try {
            ZoneId zone = TimeZoneMapper.resolveZone(timezone);
            InterviewPanelDto result = panelInterviewService.createPanelInterview(user, TimeZoneMapper.toUtc(dto, zone));
            return ResponseEntity.status(HttpStatus.CREATED).body(result);
        } catch (Exception e) {
            logger.error("Failed to create panel interview: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of("message", e.getMessage()));
        }
    }

    @DeleteMapping("/{panelId}")
    public ResponseEntity<?> cancelPanelInterview(
            @AuthenticationPrincipal User user,
            @PathVariable Long panelId) {
        try {
            panelInterviewService.cancelPanelInterview(user, panelId);
            return ResponseEntity.noContent().build();
        } catch (Exception e) {
            logger.error("Failed to cancel panel interview {}: {}", panelId, e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of("message", e.getMessage()));
        }
    }

    @GetMapping("/candidate/{candidateId}")
    public ResponseEntity<?> getPanelsByCandidateId(@PathVariable Long candidateId) {
        try {
            List<InterviewPanelDto> result = panelInterviewService.getPanelsByCandidateId(candidateId);
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            logger.error("Failed to get panels for candidate {}: {}", candidateId, e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("message", e.getMessage()));
        }
    }

    @GetMapping("/my-panels")
    public ResponseEntity<?> getMyPanels(
            @AuthenticationPrincipal User user,
            @RequestParam(required = false) Integer size,
            @RequestParam(required = false) Long departmentId,
            @RequestParam(required = false) Integer minTierId,
            @RequestParam(required = false) Integer exactTierId) {
        try {
            List<InterviewPanelDto> result = size != null
                    ? panelInterviewService.getPanelsByRequestedBy(user.getId(), size, departmentId, minTierId, exactTierId)
                    : panelInterviewService.getPanelsByRequestedBy(user.getId(), departmentId, minTierId, exactTierId);
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            logger.error("Failed to get panels for user {}: {}", user.getId(), e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("message", e.getMessage()));
        }
    }
}