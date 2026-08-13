package com.nemal.controller;

import com.nemal.dto.CatalogTypeDto;
import com.nemal.dto.CreateCatalogTypeDto;
import com.nemal.dto.ExcelImportResultDto;
import com.nemal.dto.UpdateCatalogTypeDto;
import com.nemal.service.DocumentTypeService;
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
@RequestMapping("/api/document-types")
@CrossOrigin(origins = "http://localhost:5173")
public class DocumentTypeController {

    private final DocumentTypeService documentTypeService;
    private final ExcelImportExportService excelImportExportService;

    public DocumentTypeController(
            DocumentTypeService documentTypeService,
            ExcelImportExportService excelImportExportService
    ) {
        this.documentTypeService = documentTypeService;
        this.excelImportExportService = excelImportExportService;
    }

    @GetMapping
    public ResponseEntity<List<CatalogTypeDto>> listActive() {
        return ResponseEntity.ok(documentTypeService.listActive());
    }

    @GetMapping("/all")
    public ResponseEntity<List<CatalogTypeDto>> listAll() {
        return ResponseEntity.ok(documentTypeService.listAll());
    }

    @GetMapping("/export")
    public ResponseEntity<byte[]> export() {
        return ExcelHelper.downloadResponse(excelImportExportService.exportDocumentTypes(), "document-types.xlsx");
    }

    @PostMapping(value = "/import", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<?> importTypes(@RequestParam("file") MultipartFile file) {
        try {
            ExcelImportResultDto result = excelImportExportService.importDocumentTypes(file);
            return ResponseEntity.ok(result);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        }
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
