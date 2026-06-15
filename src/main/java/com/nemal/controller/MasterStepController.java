package com.nemal.controller;

import com.nemal.dto.CandidateStepDto;
import com.nemal.service.MasterStepService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/masterSteps")
@CrossOrigin(origins = "http://localhost:5173")
public class MasterStepController {
    private final MasterStepService masterStepService;

    public MasterStepController(MasterStepService masterStepService) {
        this.masterStepService = masterStepService;
    }

    @GetMapping
    public ResponseEntity<List<CandidateStepDto>> getCandidateSteps() {
        return ResponseEntity.ok(masterStepService.getActiveAndVisibleCandidateSteps());
    }

    @GetMapping("/closing")
    public ResponseEntity<List<CandidateStepDto>> getClosingSteps() {
        return ResponseEntity.ok(masterStepService.getClosingCandidateSteps());
    }
}
