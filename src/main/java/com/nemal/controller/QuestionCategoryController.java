package com.nemal.controller;

import com.nemal.dto.CreateQuestionCategoryDto;
import com.nemal.dto.ExcelImportResultDto;
import com.nemal.dto.QuestionCategoryDto;
import com.nemal.dto.UpdateQuestionCategoryDto;
import com.nemal.service.ExcelImportExportService;
import com.nemal.service.QuestionCategoryService;
import com.nemal.util.ExcelHelper;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/question-categories")
@CrossOrigin(origins = "http://localhost:5173")
public class QuestionCategoryController {
    private final QuestionCategoryService categoryService;
    private final ExcelImportExportService excelImportExportService;

    public QuestionCategoryController(
            QuestionCategoryService categoryService,
            ExcelImportExportService excelImportExportService
    ) {
        this.categoryService = categoryService;
        this.excelImportExportService = excelImportExportService;
    }

    @GetMapping
    public ResponseEntity<List<QuestionCategoryDto>> getAllCategories(
            @RequestParam(defaultValue = "false") boolean forForms
    ) {
        return ResponseEntity.ok(categoryService.getActiveCategories(forForms));
    }

    @GetMapping("/export")
    public ResponseEntity<byte[]> exportCategories() {
        return ExcelHelper.downloadResponse(
                excelImportExportService.exportQuestionCategories(),
                "question-categories.xlsx"
        );
    }

    @PostMapping(value = "/import", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<?> importCategories(@RequestParam("file") MultipartFile file) {
        try {
            ExcelImportResultDto result = excelImportExportService.importQuestionCategories(file);
            return ResponseEntity.ok(result);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        }
    }

    @GetMapping("/{id}")
    public ResponseEntity<QuestionCategoryDto> getCategoryById(@PathVariable Long id) {
        return ResponseEntity.ok(categoryService.getCategoryById(id));
    }

    @PostMapping
    public ResponseEntity<QuestionCategoryDto> createCategory(
            @RequestBody CreateQuestionCategoryDto dto
    ) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(categoryService.createCategory(dto));
    }

    @PutMapping("/{id}")
    public ResponseEntity<QuestionCategoryDto> updateCategory(
            @PathVariable Long id,
            @RequestBody UpdateQuestionCategoryDto dto
    ) {
        return ResponseEntity.ok(categoryService.updateCategory(id, dto));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteCategory(@PathVariable Long id) {
        categoryService.deleteCategory(id);
        return ResponseEntity.noContent().build();
    }
}
