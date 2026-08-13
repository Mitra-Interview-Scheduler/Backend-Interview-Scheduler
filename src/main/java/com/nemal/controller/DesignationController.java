package com.nemal.controller;

import com.nemal.dto.CreateDesignationDto;
import com.nemal.dto.DesignationDto;
import com.nemal.dto.ExcelImportResultDto;
import com.nemal.dto.UpdateDesignationDto;
import com.nemal.service.DesignationService;
import com.nemal.service.ExcelImportExportService;
import com.nemal.util.ExcelHelper;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/designations")
@CrossOrigin(origins = "http://localhost:5173")
public class DesignationController {

    private final DesignationService designationService;
    private final ExcelImportExportService excelImportExportService;

    public DesignationController(
            DesignationService designationService,
            ExcelImportExportService excelImportExportService
    ) {
        this.designationService = designationService;
        this.excelImportExportService = excelImportExportService;
    }

    @GetMapping
    public ResponseEntity<List<DesignationDto>> getAllDesignations() {
        return ResponseEntity.ok(designationService.getAllDesignations());
    }

    @GetMapping("/export")
    public ResponseEntity<byte[]> exportDesignations() {
        return ExcelHelper.downloadResponse(excelImportExportService.exportDesignations(), "designations.xlsx");
    }

    @PostMapping(value = "/import", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<?> importDesignations(@RequestParam("file") MultipartFile file) {
        try {
            ExcelImportResultDto result = excelImportExportService.importDesignations(file);
            return ResponseEntity.ok(result);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        }
    }

    @GetMapping("/{id}")
    public ResponseEntity<DesignationDto> getDesignationById(@PathVariable Long id) {
        return ResponseEntity.ok(designationService.getDesignationById(id));
    }

    @GetMapping("/department/{departmentId}")
    public ResponseEntity<List<DesignationDto>> getDesignationsByDepartment(
            @PathVariable Long departmentId) {
        return ResponseEntity.ok(designationService.getDesignationsByDepartment(departmentId));
    }

    @GetMapping("/tier/{tierId}")
    public ResponseEntity<List<DesignationDto>> getDesignationsByTier(
            @PathVariable Long tierId) {
        return ResponseEntity.ok(designationService.getDesignationsByTier(tierId));
    }

    @PostMapping
    public ResponseEntity<DesignationDto> createDesignation(
            @RequestBody CreateDesignationDto dto) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(designationService.createDesignation(dto));
    }

    @PutMapping("/{id}")
    public ResponseEntity<DesignationDto> updateDesignation(
            @PathVariable Long id,
            @RequestBody UpdateDesignationDto dto) {
        return ResponseEntity.ok(designationService.updateDesignation(id, dto));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteDesignation(@PathVariable Long id) {
        designationService.deleteDesignation(id);
        return ResponseEntity.noContent().build();
    }
}
