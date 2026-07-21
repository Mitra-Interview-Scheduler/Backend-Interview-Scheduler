package com.nemal.controller;

import com.nemal.dto.CreateInterviewTypeDto;
import com.nemal.dto.InterviewTypeDto;
import com.nemal.dto.UpdateInterviewTypeDto;
import com.nemal.service.InterviewTypeService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/interview-types")
@CrossOrigin(origins = "http://localhost:5173")
public class InterviewTypeController {

    private static final Logger logger = LoggerFactory.getLogger(InterviewTypeController.class);
    private final InterviewTypeService interviewTypeService;

    public InterviewTypeController(InterviewTypeService interviewTypeService) {
        this.interviewTypeService = interviewTypeService;
    }

    @GetMapping
    public ResponseEntity<List<InterviewTypeDto>> getAll(
            @RequestParam(name = "activeOnly", defaultValue = "false") boolean activeOnly) {
        return ResponseEntity.ok(activeOnly
                ? interviewTypeService.getActive()
                : interviewTypeService.getAll());
    }

    @PostMapping
    public ResponseEntity<?> create(@RequestBody CreateInterviewTypeDto dto) {
        try {
            return ResponseEntity.status(HttpStatus.CREATED).body(interviewTypeService.create(dto));
        } catch (Exception e) {
            logger.warn("Failed to create interview type: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("message", e.getMessage()));
        }
    }

    @PutMapping("/{id}")
    public ResponseEntity<?> update(@PathVariable Long id, @RequestBody UpdateInterviewTypeDto dto) {
        try {
            return ResponseEntity.ok(interviewTypeService.update(id, dto));
        } catch (Exception e) {
            logger.warn("Failed to update interview type {}: {}", id, e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("message", e.getMessage()));
        }
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<?> delete(@PathVariable Long id) {
        try {
            interviewTypeService.delete(id);
            return ResponseEntity.noContent().build();
        } catch (Exception e) {
            logger.warn("Failed to delete interview type {}: {}", id, e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("message", e.getMessage()));
        }
    }

    @GetMapping("/{code}/resolve-filters")
    public ResponseEntity<?> resolveFilters(
            @PathVariable String code,
            @RequestParam Long candidateId) {
        try {
            return ResponseEntity.ok(interviewTypeService.resolveInterviewerFilters(code, candidateId));
        } catch (Exception e) {
            logger.warn("Failed to resolve filters for type {} / candidate {}: {}", code, candidateId, e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("message", e.getMessage()));
        }
    }
}
