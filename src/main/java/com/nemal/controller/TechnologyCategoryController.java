package com.nemal.controller;

import com.nemal.dto.CreateTechnologyCategoryDto;
import com.nemal.dto.TechnologyCategoryDto;
import com.nemal.dto.UpdateTechnologyCategoryDto;
import com.nemal.service.TechnologyCategoryService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/technology-categories")
@CrossOrigin(origins = "http://localhost:5173")
public class TechnologyCategoryController {
    private final TechnologyCategoryService categoryService;

    public TechnologyCategoryController(TechnologyCategoryService categoryService) {
        this.categoryService = categoryService;
    }

    @GetMapping
    public ResponseEntity<List<TechnologyCategoryDto>> getAllCategories() {
        return ResponseEntity.ok(categoryService.getActiveCategories());
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
