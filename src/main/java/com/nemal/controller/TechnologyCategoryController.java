package com.nemal.controller;

import com.nemal.dto.CreateTechnologyCategoryDto;
import com.nemal.dto.ExcelImportResultDto;
import com.nemal.dto.TechnologyCategoryDto;
import com.nemal.dto.UpdateTechnologyCategoryDto;
import com.nemal.service.ExcelImportExportService;
import com.nemal.service.TechnologyCategoryService;
import com.nemal.util.ExcelHelper;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/technology-categories")
@CrossOrigin(origins = "http://localhost:5173")
public class TechnologyCategoryController {
    private final TechnologyCategoryService categoryService;
    private final ExcelImportExportService excelImportExportService;

    public TechnologyCategoryController(
            TechnologyCategoryService categoryService,
            ExcelImportExportService excelImportExportService
    ) {
        this.categoryService = categoryService;
        this.excelImportExportService = excelImportExportService;
    }

    @GetMapping
    public ResponseEntity<List<TechnologyCategoryDto>> getAllCategories() {
        return ResponseEntity.ok(categoryService.getActiveCategories());
    }

    @GetMapping("/export")
    public ResponseEntity<byte[]> exportCategories() {
        return ExcelHelper.downloadResponse(
                excelImportExportService.exportTechnologyCategories(),
                "technology-categories.xlsx"
        );
    }

    @PostMapping(value = "/import", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<?> importCategories(@RequestParam("file") MultipartFile file) {
        try {
            ExcelImportResultDto result = excelImportExportService.importTechnologyCategories(file);
            return ResponseEntity.ok(result);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        }
    }

    @GetMapping("/{id}")
    public ResponseEntity<TechnologyCategoryDto> getCategoryById(@PathVariable Long id) {
        return ResponseEntity.ok(categoryService.getCategoryById(id));
    }

    @PostMapping
    public ResponseEntity<TechnologyCategoryDto> createCategory(
            @RequestBody CreateTechnologyCategoryDto dto
    ) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(categoryService.createCategory(dto));
    }

    @PutMapping("/{id}")
    public ResponseEntity<TechnologyCategoryDto> updateCategory(
            @PathVariable Long id,
            @RequestBody UpdateTechnologyCategoryDto dto
    ) {
        return ResponseEntity.ok(categoryService.updateCategory(id, dto));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteCategory(@PathVariable Long id) {
        categoryService.deleteCategory(id);
        return ResponseEntity.noContent().build();
    }
}
