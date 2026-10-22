package com.nemal.controller;


import com.nemal.dto.CandidatePipelineStatusEventDto;
import com.nemal.entity.CandidateStepPipeline;
import com.nemal.entity.User;
import com.nemal.service.CandidatePipelineAuditService;
import com.nemal.service.CandidateService;
import com.nemal.service.CandidateStepPipelineService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/candidatePipeline")
@CrossOrigin(origins = "http://localhost:5173")
public class CandidateStatusPipelineController {
    private final CandidateStepPipelineService candidateStepPipelineService;
    private final CandidatePipelineAuditService candidatePipelineAuditService;
    private final CandidateService candidateService;

    public CandidateStatusPipelineController(CandidateStepPipelineService candidateStepPipelineService,
                                             CandidatePipelineAuditService candidatePipelineAuditService,
                                             CandidateService candidateService) {
        this.candidateStepPipelineService = candidateStepPipelineService;
        this.candidatePipelineAuditService = candidatePipelineAuditService;
        this.candidateService = candidateService;
    }

    @GetMapping("/{candidateId}")
    public ResponseEntity<?> getUserPipeline(
            @AuthenticationPrincipal User user,
            @PathVariable Long candidateId) {
        candidateService.assertCanReadCandidate(user, candidateId);
        List<CandidateStepPipeline> candidateSteps = candidateStepPipelineService.getPipelineForCandidate(candidateId);
        return ResponseEntity.ok(candidateSteps);
    }

    @GetMapping("/{candidateId}/status-events")
    public ResponseEntity<List<CandidatePipelineStatusEventDto>> getPipelineStatusEvents(
            @AuthenticationPrincipal User user,
            @PathVariable Long candidateId) {
        candidateService.assertCanReadCandidate(user, candidateId);
        return ResponseEntity.ok(candidatePipelineAuditService.getEventsForCandidate(candidateId));
    }

    @PostMapping("/{candidateId}")
    public ResponseEntity<?> initiateUserPipeline(@PathVariable Long candidateId) {
        candidateStepPipelineService.initializeDefaultPipeline(candidateId);
        return ResponseEntity.ok().build();
    }
}
