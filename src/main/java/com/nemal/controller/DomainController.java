package com.nemal.controller;

import com.nemal.dto.CreateDomainDto;
import com.nemal.dto.DomainDto;
import com.nemal.dto.ExcelImportResultDto;
import com.nemal.dto.UpdateDomainDto;
import com.nemal.service.DomainService;
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
@RequestMapping("/api/domains")
@CrossOrigin(origins = "http://localhost:5173")
public class DomainController {

    private final DomainService domainService;
    private final ExcelImportExportService excelImportExportService;

    public DomainController(DomainService domainService, ExcelImportExportService excelImportExportService) {
        this.domainService = domainService;
        this.excelImportExportService = excelImportExportService;
    }

    @GetMapping
    public ResponseEntity<List<DomainDto>> getAllDomains() {
        return ResponseEntity.ok(domainService.getAllDomains());
    }

    @GetMapping("/all")
    public ResponseEntity<List<DomainDto>> getAllDomainsIncludingInactive() {
        return ResponseEntity.ok(domainService.getAllDomainsIncludingInactive());
    }

    @GetMapping("/export")
    public ResponseEntity<byte[]> exportDomains() {
        return ExcelHelper.downloadResponse(excelImportExportService.exportDomains(), "domains.xlsx");
    }

    @PostMapping(value = "/import", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<?> importDomains(@RequestParam("file") MultipartFile file) {
        try {
            ExcelImportResultDto result = excelImportExportService.importDomains(file);
            return ResponseEntity.ok(result);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        }
    }

    @GetMapping("/{id}")
    public ResponseEntity<DomainDto> getDomainById(@PathVariable Long id) {
        return ResponseEntity.ok(domainService.getDomainById(id));
    }

    @PostMapping
    public ResponseEntity<DomainDto> createDomain(@RequestBody CreateDomainDto dto) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(domainService.createDomain(dto));
    }

    @PutMapping("/{id}")
    public ResponseEntity<DomainDto> updateDomain(
            @PathVariable Long id,
            @RequestBody UpdateDomainDto dto) {
        return ResponseEntity.ok(domainService.updateDomain(id, dto));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteDomain(@PathVariable Long id) {
        domainService.deleteDomain(id);
        return ResponseEntity.noContent().build();
    }
}
