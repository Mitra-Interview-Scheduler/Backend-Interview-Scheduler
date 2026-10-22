package com.nemal.controller;

import com.nemal.dto.CreateDesignationDto;
import com.nemal.dto.DesignationDto;
import com.nemal.dto.UpdateDesignationDto;
import com.nemal.service.DesignationService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/designations")
@CrossOrigin(origins = "http://localhost:5173")
public class DesignationController {

    private final DesignationService designationService;

    public DesignationController(DesignationService designationService) {
        this.designationService = designationService;
    }

    @GetMapping
    public ResponseEntity<List<DesignationDto>> getAllDesignations() {
        return ResponseEntity.ok(designationService.getAllDesignations());
    }

    @GetMapping("/all")
    public ResponseEntity<List<DesignationDto>> getAllDesignationsIncludingInactive() {
        return ResponseEntity.ok(designationService.getAllDesignationsIncludingInactive());
    }

    @GetMapping("/{id}")
    public ResponseEntity<DesignationDto> getDesignationById(@PathVariable Long id) {
        return ResponseEntity.ok(designationService.getDesignationById(id));
    }

    @GetMapping("/department/{departmentId}")
    public ResponseEntity<List<DesignationDto>> getDesignationsByDepartment(
            @PathVariable Long departmentId) {
        return ResponseEntity.ok(designationService.getDesignationsByDepartment(departmentId));
    }

    @GetMapping("/tier/{tierId}")
    public ResponseEntity<List<DesignationDto>> getDesignationsByTier(
            @PathVariable Long tierId) {
        return ResponseEntity.ok(designationService.getDesignationsByTier(tierId));
    }

    @PostMapping
    public ResponseEntity<DesignationDto> createDesignation(
            @RequestBody CreateDesignationDto dto) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(designationService.createDesignation(dto));
    }

    @PutMapping("/{id}")
    public ResponseEntity<DesignationDto> updateDesignation(
            @PathVariable Long id,
            @RequestBody UpdateDesignationDto dto) {
        return ResponseEntity.ok(designationService.updateDesignation(id, dto));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteDesignation(@PathVariable Long id) {
        designationService.deleteDesignation(id);
        return ResponseEntity.noContent().build();
    }
}