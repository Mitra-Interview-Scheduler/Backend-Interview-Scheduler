package com.nemal.controller;

import com.nemal.dto.CreateQuestionCategoryDto;
import com.nemal.dto.QuestionCategoryDto;
import com.nemal.dto.UpdateQuestionCategoryDto;
import com.nemal.service.QuestionCategoryService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/question-categories")
@CrossOrigin(origins = "http://localhost:5173")
public class QuestionCategoryController {
    private final QuestionCategoryService categoryService;

    public QuestionCategoryController(QuestionCategoryService categoryService) {
        this.categoryService = categoryService;
    }

    @GetMapping
    public ResponseEntity<List<QuestionCategoryDto>> getAllCategories(
            @RequestParam(defaultValue = "false") boolean forForms
    ) {
        return ResponseEntity.ok(categoryService.getActiveCategories(forForms));
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
