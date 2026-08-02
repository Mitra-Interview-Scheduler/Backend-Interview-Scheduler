package com.nemal.controller;

import com.nemal.dto.CatalogTypeDto;
import com.nemal.dto.CreateCatalogTypeDto;
import com.nemal.dto.UpdateCatalogTypeDto;
import com.nemal.service.ResourceTypeService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/resource-types")
@CrossOrigin(origins = "http://localhost:5173")
public class ResourceTypeController {

    private final ResourceTypeService resourceTypeService;

    public ResourceTypeController(ResourceTypeService resourceTypeService) {
        this.resourceTypeService = resourceTypeService;
    }

    @GetMapping
    public ResponseEntity<List<CatalogTypeDto>> listActive() {
        return ResponseEntity.ok(resourceTypeService.listActive());
    }

    @GetMapping("/all")
    public ResponseEntity<List<CatalogTypeDto>> listAll() {
        return ResponseEntity.ok(resourceTypeService.listAll());
    }

    @PostMapping
    public ResponseEntity<?> create(@RequestBody CreateCatalogTypeDto dto) {
        try {
            return ResponseEntity.status(HttpStatus.CREATED).body(resourceTypeService.create(dto));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        }
    }

    @PutMapping("/{id}")
    public ResponseEntity<?> update(@PathVariable Long id, @RequestBody UpdateCatalogTypeDto dto) {
        try {
            return ResponseEntity.ok(resourceTypeService.update(id, dto));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        } catch (RuntimeException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("message", e.getMessage()));
        }
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<?> delete(@PathVariable Long id) {
        try {
            resourceTypeService.delete(id);
            return ResponseEntity.noContent().build();
        } catch (RuntimeException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("message", e.getMessage()));
        }
    }
}
