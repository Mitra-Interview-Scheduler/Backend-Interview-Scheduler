package com.nemal.controller;

import com.nemal.dto.CandidateStepDto;
import com.nemal.service.CandidateStepService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/candidate-steps")
@CrossOrigin(origins = "http://localhost:5173")
public class CandidateStepController {
    private final CandidateStepService candidateStepService;

    public CandidateStepController(CandidateStepService candidateStepService) {
        this.candidateStepService = candidateStepService;
    }

    @GetMapping
    public ResponseEntity<List<CandidateStepDto>> getCandidateSteps() {
        return ResponseEntity.ok(candidateStepService.getActiveCandidateSteps());
    }
}
