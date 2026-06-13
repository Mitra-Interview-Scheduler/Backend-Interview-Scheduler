package com.nemal.controller;


import com.nemal.dto.CandidateDto;
import com.nemal.entity.CandidateStepPipeline;
import com.nemal.service.CandidateService;
import com.nemal.service.CandidateStepPipelineService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/candidatePipeline")
@CrossOrigin(origins = "http://localhost:5173")
public class CandidateStatusPipelineController {
    private final CandidateStepPipelineService candidateStepPipelineService;
    private final CandidateService  candidateService;


    public CandidateStatusPipelineController(CandidateStepPipelineService candidateStepPipelineService, CandidateService candidateService) {
        this.candidateStepPipelineService = candidateStepPipelineService;
        this.candidateService = candidateService;
    }



    @GetMapping("/{candidateId}")
    public ResponseEntity<?> getUserPipeline(@PathVariable Long candidateId){
        List<CandidateStepPipeline> candidateSteps = candidateStepPipelineService.getPipelineForCandidate(candidateId);
        return ResponseEntity.ok(candidateSteps);

    }

    @PostMapping("/{candidateId}")
    public ResponseEntity<?> initiateUserPipeline(@PathVariable Long candidateId){
        candidateStepPipelineService.initializeDefaultPipeline(candidateId);
        return ResponseEntity.ok().build();
    }



}
