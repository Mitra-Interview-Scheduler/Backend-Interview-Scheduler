package com.nemal.controller;

import com.nemal.dto.CreateDomainDto;
import com.nemal.dto.DomainDto;
import com.nemal.dto.UpdateDomainDto;
import com.nemal.service.DomainService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/domains")
@CrossOrigin(origins = "http://localhost:5173")
public class DomainController {

    private final DomainService domainService;

    public DomainController(DomainService domainService) {
        this.domainService = domainService;
    }

    @GetMapping
    public ResponseEntity<List<DomainDto>> getAllDomains() {
        return ResponseEntity.ok(domainService.getAllDomains());
    }

    @GetMapping("/all")
    public ResponseEntity<List<DomainDto>> getAllDomainsIncludingInactive() {
        return ResponseEntity.ok(domainService.getAllDomainsIncludingInactive());
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
