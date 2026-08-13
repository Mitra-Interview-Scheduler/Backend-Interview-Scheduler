package com.nemal.controller;

import com.nemal.dto.CreateInterviewTypeDto;
import com.nemal.dto.ExcelImportResultDto;
import com.nemal.dto.InterviewTypeDeletePreviewDto;
import com.nemal.dto.InterviewTypeDeleteResultDto;
import com.nemal.dto.InterviewTypeDto;
import com.nemal.dto.UpdateInterviewTypeDto;
import com.nemal.service.ExcelImportExportService;
import com.nemal.service.InterviewTypeService;
import com.nemal.util.ExcelHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/interview-types")
@CrossOrigin(origins = "http://localhost:5173")
public class InterviewTypeController {

    private static final Logger logger = LoggerFactory.getLogger(InterviewTypeController.class);
    private final InterviewTypeService interviewTypeService;
    private final ExcelImportExportService excelImportExportService;

    public InterviewTypeController(
            InterviewTypeService interviewTypeService,
            ExcelImportExportService excelImportExportService
    ) {
        this.interviewTypeService = interviewTypeService;
        this.excelImportExportService = excelImportExportService;
    }

    @GetMapping
    public ResponseEntity<List<InterviewTypeDto>> getAll(
            @RequestParam(name = "activeOnly", defaultValue = "false") boolean activeOnly) {
        return ResponseEntity.ok(activeOnly
                ? interviewTypeService.getActive()
                : interviewTypeService.getAll());
    }

    @GetMapping("/export")
    public ResponseEntity<byte[]> export() {
        return ExcelHelper.downloadResponse(excelImportExportService.exportInterviewTypes(), "interview-types.xlsx");
    }

    @PostMapping(value = "/import", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<?> importTypes(@RequestParam("file") MultipartFile file) {
        try {
            ExcelImportResultDto result = excelImportExportService.importInterviewTypes(file);
            return ResponseEntity.ok(result);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        }
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

    @PatchMapping("/{id}/reactivate")
    public ResponseEntity<?> reactivate(@PathVariable Long id) {
        try {
            return ResponseEntity.ok(interviewTypeService.reactivate(id));
        } catch (Exception e) {
            logger.warn("Failed to reactivate interview type {}: {}", id, e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("message", e.getMessage()));
        }
    }

    @GetMapping("/{id}/delete-preview")
    public ResponseEntity<?> getDeletePreview(@PathVariable Long id) {
        try {
            return ResponseEntity.ok(interviewTypeService.getDeletePreview(id));
        } catch (Exception e) {
            logger.warn("Failed to load delete preview for interview type {}: {}", id, e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("message", e.getMessage()));
        }
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<?> delete(@PathVariable Long id) {
        try {
            return ResponseEntity.ok(interviewTypeService.delete(id));
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
