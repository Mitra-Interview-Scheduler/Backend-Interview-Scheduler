package com.nemal.controller;

import com.nemal.dto.ClosingReasonDto;
import com.nemal.service.ClosingReasonService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/closing-reasons")
@CrossOrigin(origins = "http://localhost:5173")
public class ClosingReasonController {
    private final ClosingReasonService closingReasonService;

    public ClosingReasonController(ClosingReasonService closingReasonService) {
        this.closingReasonService = closingReasonService;
    }

    @GetMapping
    public ResponseEntity<List<ClosingReasonDto>> getActiveReasons() {
        return ResponseEntity.ok(closingReasonService.getActiveReasons());
    }
}
