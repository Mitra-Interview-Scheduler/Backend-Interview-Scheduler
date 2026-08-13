package com.nemal.controller;

import com.nemal.dto.CreateTierDto;
import com.nemal.dto.ExcelImportResultDto;
import com.nemal.dto.TierDto;
import com.nemal.dto.UpdateTierDto;
import com.nemal.service.ExcelImportExportService;
import com.nemal.service.TierService;
import com.nemal.util.ExcelHelper;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/tiers")
@CrossOrigin(origins = "http://localhost:5173")
public class TierController {

    private final TierService tierService;
    private final ExcelImportExportService excelImportExportService;

    public TierController(TierService tierService, ExcelImportExportService excelImportExportService) {
        this.tierService = tierService;
        this.excelImportExportService = excelImportExportService;
    }

    @GetMapping
    public ResponseEntity<List<TierDto>> getAllTiers() {
        return ResponseEntity.ok(tierService.getAllTiers());
    }

    @GetMapping("/export")
    public ResponseEntity<byte[]> exportTiers() {
        return ExcelHelper.downloadResponse(excelImportExportService.exportTiers(), "tiers.xlsx");
    }

    @PostMapping(value = "/import", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<?> importTiers(@RequestParam("file") MultipartFile file) {
        try {
            ExcelImportResultDto result = excelImportExportService.importTiers(file);
            return ResponseEntity.ok(result);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        }
    }

    @GetMapping("/{id}")
    public ResponseEntity<TierDto> getTierById(@PathVariable Long id) {
        return ResponseEntity.ok(tierService.getTierById(id));
    }

    @GetMapping("/department/{departmentId}")
    public ResponseEntity<List<TierDto>> getTiersByDepartment(
            @PathVariable Long departmentId) {
        return ResponseEntity.ok(tierService.getTiersByDepartment(departmentId));
    }

    @PostMapping
    public ResponseEntity<TierDto> createTier(@RequestBody CreateTierDto dto) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(tierService.createTier(dto));
    }

    @PutMapping("/{id}")
    public ResponseEntity<TierDto> updateTier(
            @PathVariable Long id,
            @RequestBody UpdateTierDto dto) {
        return ResponseEntity.ok(tierService.updateTier(id, dto));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteTier(@PathVariable Long id) {
        tierService.deleteTier(id);
        return ResponseEntity.noContent().build();
    }
}
