package com.nemal.controller;

import com.nemal.dto.CandidateDto;
import com.nemal.dto.CandidateDocumentDto;
import com.nemal.dto.CreateCandidateDto;
import com.nemal.dto.PaginatedResponseDto;
import com.nemal.dto.UpdateCandidateDto;
import com.nemal.entity.CandidateDocument;
import com.nemal.enums.CandidateStatus;
import com.nemal.service.CandidateService;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/candidates")
@CrossOrigin(origins = "http://localhost:5173")
public class CandidateController {

    private final CandidateService candidateService;

    public CandidateController(CandidateService candidateService) {
        this.candidateService = candidateService;
    }

    @GetMapping("/statuses")
    public ResponseEntity<List<String>> getCandidateStatuses() {
        List<String> statuses = Arrays.stream(CandidateStatus.values())
                .map(Enum::name)
                .collect(Collectors.toList());
        return ResponseEntity.ok(statuses);
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

    @GetMapping("/{id}/documents")
    public ResponseEntity<List<CandidateDocumentDto>> getCandidateDocuments(@PathVariable Long id) {
        return ResponseEntity.ok(candidateService.getCandidateDocuments(id));
    }

    @PostMapping(value = "/{id}/documents", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<CandidateDocumentDto> uploadCandidateDocument(
            @PathVariable Long id,
            @RequestParam(defaultValue = "CV") String documentType,
            @RequestPart("file") MultipartFile file
    ) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(candidateService.uploadCandidateDocument(id, documentType, file));
    }

    @PutMapping(value = "/{id}/documents/{documentId}", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<CandidateDocumentDto> replaceCandidateDocument(
            @PathVariable Long id,
            @PathVariable Long documentId,
            @RequestParam(defaultValue = "CV") String documentType,
            @RequestPart("file") MultipartFile file
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

    @DeleteMapping("/{id}/documents/{documentId}")
    public ResponseEntity<Void> deleteCandidateDocument(
            @PathVariable Long id,
            @PathVariable Long documentId
    ) {
        candidateService.deleteCandidateDocument(id, documentId);
        return ResponseEntity.noContent().build();
    }
}
