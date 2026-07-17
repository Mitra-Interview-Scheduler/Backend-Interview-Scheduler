package com.nemal.controller;

import com.nemal.dto.AddCandidateTechnologyDto;
import com.nemal.dto.CandidateDto;
import com.nemal.dto.CandidateDocumentDto;
import com.nemal.dto.CandidateTechnologyDto;
import com.nemal.dto.CloseCandidateDto;
import com.nemal.dto.CreateCandidateDto;
import com.nemal.dto.DepartmentUserDto;
import com.nemal.dto.PaginatedResponseDto;
import com.nemal.dto.UpdateCandidateDto;
import com.nemal.dto.UpdateCandidateTechnologyDto;
import com.nemal.entity.CandidateDocument;
import com.nemal.entity.User;
import com.nemal.enums.MasterStatus;
import com.nemal.service.CandidateClosureService;
import com.nemal.service.CandidateService;
import com.nemal.service.CandidateTechnologyService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/candidates")
@CrossOrigin(origins = "http://localhost:5173")
public class CandidateController {

    @Autowired
    private final CandidateService candidateService;
    private final CandidateClosureService candidateClosureService;
    private final CandidateTechnologyService candidateTechnologyService;

    public CandidateController(CandidateService candidateService,
                               CandidateClosureService candidateClosureService,
                               CandidateTechnologyService candidateTechnologyService) {
        this.candidateService = candidateService;
        this.candidateClosureService = candidateClosureService;
        this.candidateTechnologyService = candidateTechnologyService;
    }

    @GetMapping("/statuses")
    public ResponseEntity<List<String>> getCandidateStatuses() {
        List<String> statuses = Arrays.stream(MasterStatus.values())
                .map(Enum::name)
                .collect(Collectors.toList());
        return ResponseEntity.ok(statuses);
    }

    @GetMapping("/coordinated-hr-options")
    public ResponseEntity<List<DepartmentUserDto>> getCoordinatedHrOptions(
            @RequestParam Long departmentId
    ) {
        return ResponseEntity.ok(candidateService.getCoordinatedHrOptions(departmentId));
    }

    @GetMapping
    public ResponseEntity<?> getAllCandidates(
            @RequestParam(required = false) Long departmentId,
            @RequestParam(required = false) MasterStatus status,
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

    @GetMapping("/{id}/technologies")
    public ResponseEntity<List<CandidateTechnologyDto>> getCandidateTechnologies(@PathVariable Long id) {
        return ResponseEntity.ok(candidateTechnologyService.getCandidateTechnologies(id));
    }

    @PostMapping("/{id}/technologies")
    public ResponseEntity<CandidateTechnologyDto> addCandidateTechnology(
            @PathVariable Long id,
            @RequestBody AddCandidateTechnologyDto dto
    ) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(candidateTechnologyService.addCandidateTechnology(id, dto));
    }

    @PutMapping("/{id}/technologies/{technologyAssignmentId}")
    public ResponseEntity<CandidateTechnologyDto> updateCandidateTechnology(
            @PathVariable Long id,
            @PathVariable Long technologyAssignmentId,
            @RequestBody UpdateCandidateTechnologyDto dto
    ) {
        return ResponseEntity.ok(
                candidateTechnologyService.updateCandidateTechnology(id, technologyAssignmentId, dto)
        );
    }

    @DeleteMapping("/{id}/technologies/{technologyAssignmentId}")
    public ResponseEntity<Void> removeCandidateTechnology(
            @PathVariable Long id,
            @PathVariable Long technologyAssignmentId
    ) {
        candidateTechnologyService.removeCandidateTechnology(id, technologyAssignmentId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{id}/documents")
    public ResponseEntity<List<CandidateDocumentDto>> getCandidateDocuments(@PathVariable Long id) {
        return ResponseEntity.ok(candidateService.getCandidateDocuments(id));
    }

    @PostMapping(value = "/{id}/documents", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<CandidateDocumentDto> uploadCandidateDocument(
            @PathVariable Long id,
            @RequestParam(defaultValue = "CV") String documentType,
            @RequestParam("file") MultipartFile file
    ) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(candidateService.uploadCandidateDocument(id, documentType, file));
    }

    @PutMapping(value = "/{id}/documents/{documentId}", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<CandidateDocumentDto> replaceCandidateDocument(
            @PathVariable Long id,
            @PathVariable Long documentId,
            @RequestParam(defaultValue = "CV") String documentType,
            @RequestParam("file") MultipartFile file
    ) {
        return ResponseEntity.ok(candidateService.replaceCandidateDocument(id, documentId, documentType, file));
    }

    @GetMapping("/{id}/documents/{documentId}/download")
    public ResponseEntity<byte[]> downloadCandidateDocument(
            @PathVariable Long id,
            @PathVariable Long documentId
    ) {
        CandidateDocument document = candidateService.getCandidateDocumentFile(id, documentId);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(document.getContentType()))
                .contentLength(document.getFileSize())
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                        .filename(document.getFileName())
                        .build()
                        .toString())
                .body(document.getFileData());
    }

    @GetMapping("/department/{departmentId}")
    public ResponseEntity<List<CandidateDto>> getCandidatesByDepartment(
            @PathVariable Long departmentId) {
        return ResponseEntity.ok(candidateService.getCandidatesByDepartment(departmentId));
    }

    @GetMapping("/status/{status}")
    public ResponseEntity<List<CandidateDto>> getCandidatesByStatus(
            @PathVariable MasterStatus status) {
        return ResponseEntity.ok(candidateService.getCandidatesByStatus(status));
    }

    @GetMapping("/search")
    public ResponseEntity<List<CandidateDto>> searchCandidates(
            @RequestParam String term) {
        return ResponseEntity.ok(candidateService.searchCandidates(term));
    }

    @PostMapping
    public ResponseEntity<CandidateDto> createCandidate(
            @RequestBody CreateCandidateDto dto,
            @AuthenticationPrincipal User user) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(candidateService.createCandidate(dto, user));
    }

    @PutMapping("/{id}")
    public ResponseEntity<CandidateDto> updateCandidate(
            @PathVariable Long id,
            @RequestBody UpdateCandidateDto dto,
            @AuthenticationPrincipal User user) {
        return ResponseEntity.ok(candidateService.updateCandidate(id, dto, user));
    }

    @PostMapping("/{id}/close")
    public ResponseEntity<CandidateDto> closeCandidate(
            @PathVariable Long id,
            @RequestBody CloseCandidateDto dto,
            @AuthenticationPrincipal User user) {
        return ResponseEntity.ok(candidateClosureService.closeCandidate(id, dto, user));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteCandidate(@PathVariable Long id) {
        candidateService.deleteCandidate(id);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/{id}/documents/{documentId}")
    public ResponseEntity<Void> deleteCandidateDocument(
            @PathVariable Long id,
            @PathVariable Long documentId
    ) {
        candidateService.deleteCandidateDocument(id, documentId);
        return ResponseEntity.noContent().build();
    }
}
