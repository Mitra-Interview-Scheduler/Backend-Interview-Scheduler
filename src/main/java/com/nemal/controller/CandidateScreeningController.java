package com.nemal.controller;

import com.nemal.dto.ScreeningSaveRequestDTO;
import com.nemal.dto.ScreeningResponseDTO;
import com.nemal.entity.User;
import com.nemal.service.CandidateScreeningService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/candidateScreening")
@CrossOrigin(origins = "http://localhost:5173")
@RequiredArgsConstructor
public class CandidateScreeningController {

    private final CandidateScreeningService screeningService;

    @GetMapping("/{candidateId}/screening")
    public ResponseEntity<?> getCandidateScreeningFile(@PathVariable Long candidateId) {
        try {
            ScreeningResponseDTO response = screeningService.getScreeningByCandidateId(candidateId);
            return ResponseEntity.ok(response);
        } catch (jakarta.persistence.EntityNotFoundException e) {
            log.info("Notice: No screening file found for candidate ID {}", candidateId);

            Map<String, Object> errorBody = new HashMap<>();
            errorBody.put("success", false);
            errorBody.put("message", "No screening profile has been created for this candidate yet. You can start filling out the form below to create one.");
            errorBody.put("candidateId", candidateId);

            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(errorBody);
        }
    }

    @PostMapping("/{candidateId}/screening")
    public ResponseEntity<ScreeningResponseDTO> saveCandidateScreeningFile(
            @PathVariable Long candidateId,
            @RequestBody ScreeningSaveRequestDTO payload,
            @AuthenticationPrincipal User user) {
        return ResponseEntity.ok(screeningService.saveOrUpdateScreening(candidateId, payload, user));
    }
}