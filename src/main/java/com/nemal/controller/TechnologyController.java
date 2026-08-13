package com.nemal.controller;

import com.nemal.dto.CreateTechnologyDto;
import com.nemal.dto.ExcelImportResultDto;
import com.nemal.dto.TechnologyCategoryDto;
import com.nemal.dto.TechnologyDto;
import com.nemal.dto.UpdateTechnologyDto;
import com.nemal.service.ExcelImportExportService;
import com.nemal.service.TechnologyService;
import com.nemal.util.ExcelHelper;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/technologies")
@CrossOrigin(origins = "http://localhost:5173")
public class TechnologyController {

    private final TechnologyService technologyService;
    private final ExcelImportExportService excelImportExportService;

    public TechnologyController(
            TechnologyService technologyService,
            ExcelImportExportService excelImportExportService
    ) {
        this.technologyService = technologyService;
        this.excelImportExportService = excelImportExportService;
    }

    @GetMapping
    public ResponseEntity<List<TechnologyDto>> getAllTechnologies() {
        return ResponseEntity.ok(technologyService.getAllTechnologies());
    }

    @GetMapping("/export")
    public ResponseEntity<byte[]> exportTechnologies() {
        return ExcelHelper.downloadResponse(excelImportExportService.exportTechnologies(), "technologies.xlsx");
    }

    @PostMapping(value = "/import", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<?> importTechnologies(@RequestParam("file") MultipartFile file) {
        try {
            ExcelImportResultDto result = excelImportExportService.importTechnologies(file);
            return ResponseEntity.ok(result);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        }
    }

    @GetMapping("/{id}")
    public ResponseEntity<TechnologyDto> getTechnologyById(@PathVariable Long id) {
        return ResponseEntity.ok(technologyService.getTechnologyById(id));
    }

    @GetMapping("/category/{categoryCode}")
    public ResponseEntity<List<TechnologyDto>> getTechnologiesByCategory(
            @PathVariable String categoryCode) {
        return ResponseEntity.ok(technologyService.getTechnologiesByCategoryCode(categoryCode));
    }

    @GetMapping("/categories")
    public ResponseEntity<List<TechnologyCategoryDto>> getAllCategories() {
        return ResponseEntity.ok(technologyService.getAllCategories());
    }

    @PostMapping
    public ResponseEntity<TechnologyDto> createTechnology(
            @RequestBody CreateTechnologyDto dto) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(technologyService.createTechnology(dto));
    }

    @PutMapping("/{id}")
    public ResponseEntity<TechnologyDto> updateTechnology(
            @PathVariable Long id,
            @RequestBody UpdateTechnologyDto dto) {
        return ResponseEntity.ok(technologyService.updateTechnology(id, dto));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteTechnology(@PathVariable Long id) {
        technologyService.deleteTechnology(id);
        return ResponseEntity.noContent().build();
    }
}