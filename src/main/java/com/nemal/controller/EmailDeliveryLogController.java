package com.nemal.controller;

import com.nemal.dto.EmailDeliveryLogDto;
import com.nemal.dto.PaginatedResponseDto;
import com.nemal.service.EmailDeliveryLogService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/admin/email-logs")
@PreAuthorize("hasRole('ADMIN')")
public class EmailDeliveryLogController {

    private final EmailDeliveryLogService emailDeliveryLogService;

    public EmailDeliveryLogController(EmailDeliveryLogService emailDeliveryLogService) {
        this.emailDeliveryLogService = emailDeliveryLogService;
    }

    @GetMapping
    public ResponseEntity<PaginatedResponseDto<EmailDeliveryLogDto>> list(
            @RequestParam(required = false) String search,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        return ResponseEntity.ok(emailDeliveryLogService.list(search, status, page, size));
    }

    @GetMapping("/meta")
    public ResponseEntity<Map<String, Object>> meta() {
        return ResponseEntity.ok(Map.of(
                "retentionDays", emailDeliveryLogService.getRetentionDays()
        ));
    }

    @GetMapping("/{id}")
    public ResponseEntity<?> getById(@PathVariable Long id) {
        try {
            return ResponseEntity.ok(emailDeliveryLogService.getById(id));
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.notFound().build();
        }
    }
}
