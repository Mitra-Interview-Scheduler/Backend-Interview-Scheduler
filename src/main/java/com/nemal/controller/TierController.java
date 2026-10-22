package com.nemal.controller;

import com.nemal.dto.CreateTierDto;
import com.nemal.dto.TierDto;
import com.nemal.dto.UpdateTierDto;
import com.nemal.service.TierService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/tiers")
@CrossOrigin(origins = "http://localhost:5173")
public class TierController {

    private final TierService tierService;

    public TierController(TierService tierService) {
        this.tierService = tierService;
    }

    @GetMapping
    public ResponseEntity<List<TierDto>> getAllTiers() {
        return ResponseEntity.ok(tierService.getAllTiers());
    }

    @GetMapping("/all")
    public ResponseEntity<List<TierDto>> getAllTiersIncludingInactive() {
        return ResponseEntity.ok(tierService.getAllTiersIncludingInactive());
    }

    @GetMapping("/{id}")
    public ResponseEntity<TierDto> getTierById(@PathVariable Long id) {
        return ResponseEntity.ok(tierService.getTierById(id));
    }

    @GetMapping("/department/{departmentId}")
    public ResponseEntity<List<TierDto>> getTiersByDepartment(
            @PathVariable Long departmentId) {
        return ResponseEntity.ok(tierService.getTiersByDepartment(departmentId));
    }

    @PostMapping
    public ResponseEntity<TierDto> createTier(@RequestBody CreateTierDto dto) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(tierService.createTier(dto));
    }

    @PutMapping("/{id}")
    public ResponseEntity<TierDto> updateTier(
            @PathVariable Long id,
            @RequestBody UpdateTierDto dto) {
        return ResponseEntity.ok(tierService.updateTier(id, dto));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteTier(@PathVariable Long id) {
        tierService.deleteTier(id);
        return ResponseEntity.noContent().build();
    }
}
