package com.nemal.controller;

import com.nemal.dto.CatalogTypeDto;
import com.nemal.dto.CreateCatalogTypeDto;
import com.nemal.dto.UpdateCatalogTypeDto;
import com.nemal.service.DocumentTypeService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/document-types")
@CrossOrigin(origins = "http://localhost:5173")
public class DocumentTypeController {

    private final DocumentTypeService documentTypeService;

    public DocumentTypeController(DocumentTypeService documentTypeService) {
        this.documentTypeService = documentTypeService;
    }

    @GetMapping
    public ResponseEntity<List<CatalogTypeDto>> listActive() {
        return ResponseEntity.ok(documentTypeService.listActive());
    }

    @GetMapping("/all")
    public ResponseEntity<List<CatalogTypeDto>> listAll() {
        return ResponseEntity.ok(documentTypeService.listAll());
    }

    @PostMapping
    public ResponseEntity<?> create(@RequestBody CreateCatalogTypeDto dto) {
        try {
            return ResponseEntity.status(HttpStatus.CREATED).body(documentTypeService.create(dto));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        }
    }

    @PutMapping("/{id}")
    public ResponseEntity<?> update(@PathVariable Long id, @RequestBody UpdateCatalogTypeDto dto) {
        try {
            return ResponseEntity.ok(documentTypeService.update(id, dto));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        } catch (RuntimeException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("message", e.getMessage()));
        }
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<?> delete(@PathVariable Long id) {
        try {
            documentTypeService.delete(id);
            return ResponseEntity.noContent().build();
        } catch (RuntimeException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("message", e.getMessage()));
        }
    }
}
