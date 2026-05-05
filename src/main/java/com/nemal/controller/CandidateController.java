package com.nemal.controller;

import com.nemal.dto.CandidateDto;
import com.nemal.dto.CreateCandidateDto;
import com.nemal.dto.PaginatedResponseDto;
import com.nemal.dto.UpdateCandidateDto;
import com.nemal.enums.CandidateStatus;
import com.nemal.service.CandidateService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/candidates")
@CrossOrigin(origins = "http://localhost:5173")
public class CandidateController {

    private final CandidateService candidateService;

    public CandidateController(CandidateService candidateService) {
        this.candidateService = candidateService;
    }

    @GetMapping
    public ResponseEntity<?> getAllCandidates(
            @RequestParam(required = false) Long departmentId,
            @RequestParam(required = false) CandidateStatus status,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size
    ) {
        if (page != null || size != null) {
            int pageValue = page != null ? page : 0;
            int sizeValue = size != null ? size : 10;
            PaginatedResponseDto<CandidateDto> result = candidateService.findWithFiltersPaged(
                    departmentId, status, search, pageValue, sizeValue
            );
            return ResponseEntity.ok(result);
        }

        if (departmentId != null || status != null || search != null) {
            return ResponseEntity.ok(
                    candidateService.findWithFilters(departmentId, status, search));
        }
        return ResponseEntity.ok(candidateService.getAllCandidates());
    }

    @GetMapping("/{id}")
    public ResponseEntity<CandidateDto> getCandidateById(@PathVariable Long id) {
        return ResponseEntity.ok(candidateService.getCandidateById(id));
    }

    @GetMapping("/department/{departmentId}")
    public ResponseEntity<List<CandidateDto>> getCandidatesByDepartment(
            @PathVariable Long departmentId) {
        return ResponseEntity.ok(candidateService.getCandidatesByDepartment(departmentId));
    }

    @GetMapping("/status/{status}")
    public ResponseEntity<List<CandidateDto>> getCandidatesByStatus(
            @PathVariable CandidateStatus status) {
        return ResponseEntity.ok(candidateService.getCandidatesByStatus(status));
    }

    @GetMapping("/search")
    public ResponseEntity<List<CandidateDto>> searchCandidates(
            @RequestParam String term) {
        return ResponseEntity.ok(candidateService.searchCandidates(term));
    }

    @PostMapping
    public ResponseEntity<CandidateDto> createCandidate(
            @RequestBody CreateCandidateDto dto) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(candidateService.createCandidate(dto));
    }

    @PutMapping("/{id}")
    public ResponseEntity<CandidateDto> updateCandidate(
            @PathVariable Long id,
            @RequestBody UpdateCandidateDto dto) {
        return ResponseEntity.ok(candidateService.updateCandidate(id, dto));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteCandidate(@PathVariable Long id) {
        candidateService.deleteCandidate(id);
        return ResponseEntity.noContent().build();
    }
}