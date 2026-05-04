package com.nemal.controller;

import com.nemal.dto.AvailabilitySlotDto;
import com.nemal.dto.BulkAvailabilitySlotDto;
import com.nemal.dto.CreateAvailabilitySlotDto;
import com.nemal.dto.UpdateAvailabilitySlotDto;
import com.nemal.entity.User;
import com.nemal.service.AvailabilityService;
import com.nemal.util.TimeZoneMapper;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/availability")
@CrossOrigin(origins = "http://localhost:5173")
public class AvailabilityController {

    private final AvailabilityService availabilityService;

    public AvailabilityController(AvailabilityService availabilityService) {
        this.availabilityService = availabilityService;
    }

    @GetMapping
    public ResponseEntity<List<AvailabilitySlotDto>> getMyAvailability(
            @AuthenticationPrincipal User user,
            @RequestHeader(value = "X-Timezone", required = false) String timezone
    ) {
        ZoneId zone = TimeZoneMapper.resolveZone(timezone);
        List<AvailabilitySlotDto> slots = availabilityService.getInterviewerAvailability(user);
        return ResponseEntity.ok(TimeZoneMapper.fromUtcAvailability(slots, zone));
    }

    @GetMapping("/range")
    public ResponseEntity<List<AvailabilitySlotDto>> getAvailabilityByDateRange(
            @AuthenticationPrincipal User user,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime start,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime end,
            @RequestHeader(value = "X-Timezone", required = false) String timezone
    ) {
        ZoneId zone = TimeZoneMapper.resolveZone(timezone);
        LocalDateTime utcStart = TimeZoneMapper.toUtc(start, zone);
        LocalDateTime utcEnd = TimeZoneMapper.toUtc(end, zone);
        return ResponseEntity.ok(
                TimeZoneMapper.fromUtcAvailability(
                        availabilityService.getInterviewerAvailabilityByDateRange(user, utcStart, utcEnd),
                        zone
                )
        );
    }

    @PostMapping
    public ResponseEntity<AvailabilitySlotDto> createAvailabilitySlot(
            @AuthenticationPrincipal User user,
            @RequestBody CreateAvailabilitySlotDto dto,
            @RequestHeader(value = "X-Timezone", required = false) String timezone
    ) {
        ZoneId zone = TimeZoneMapper.resolveZone(timezone);
        CreateAvailabilitySlotDto utcDto = new CreateAvailabilitySlotDto(
                TimeZoneMapper.toUtc(dto.startDateTime(), zone),
                TimeZoneMapper.toUtc(dto.endDateTime(), zone),
                dto.description()
        );
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(TimeZoneMapper.fromUtc(availabilityService.createAvailabilitySlot(user, utcDto), zone));
    }

    @PostMapping("/bulk")
    public ResponseEntity<List<AvailabilitySlotDto>> createBulkAvailabilitySlots(
            @AuthenticationPrincipal User user,
            @RequestBody BulkAvailabilitySlotDto dto,
            @RequestHeader(value = "X-Timezone", required = false) String timezone
    ) {
        ZoneId zone = TimeZoneMapper.resolveZone(timezone);
        BulkAvailabilitySlotDto utcDto = new BulkAvailabilitySlotDto(
                dto.slots().stream().map(slot -> new CreateAvailabilitySlotDto(
                        TimeZoneMapper.toUtc(slot.startDateTime(), zone),
                        TimeZoneMapper.toUtc(slot.endDateTime(), zone),
                        slot.description()
                )).toList()
        );
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(TimeZoneMapper.fromUtcAvailability(availabilityService.createBulkAvailabilitySlots(user, utcDto), zone));
    }

    /**
     * Update an existing AVAILABLE slot (interviewer only).
     * Returns 400 if the slot is BOOKED (has a scheduled interview).
     */
    @PutMapping("/{slotId}")
    public ResponseEntity<?> updateAvailabilitySlot(
            @AuthenticationPrincipal User user,
            @PathVariable Long slotId,
            @RequestBody UpdateAvailabilitySlotDto dto,
            @RequestHeader(value = "X-Timezone", required = false) String timezone
    ) {
        try {
            ZoneId zone = TimeZoneMapper.resolveZone(timezone);
            UpdateAvailabilitySlotDto utcDto = new UpdateAvailabilitySlotDto(
                    TimeZoneMapper.toUtc(dto.startDateTime(), zone),
                    TimeZoneMapper.toUtc(dto.endDateTime(), zone),
                    dto.description()
            );
            AvailabilitySlotDto result = availabilityService.updateAvailabilitySlot(user, slotId, utcDto);
            return ResponseEntity.ok(TimeZoneMapper.fromUtc(result, zone));
        } catch (RuntimeException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of("message", e.getMessage()));
        }
    }

    @DeleteMapping("/{slotId}")
    public ResponseEntity<Void> deleteAvailabilitySlot(
            @AuthenticationPrincipal User user,
            @PathVariable Long slotId
    ) {
        availabilityService.deleteAvailabilitySlot(user, slotId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/stats")
    public ResponseEntity<Map<String, Long>> getAvailabilityStats(
            @AuthenticationPrincipal User user
    ) {
        return ResponseEntity.ok(Map.of(
                "availableSlots", availabilityService.getAvailableSlotCount(user.getId()),
                "bookedSlots", availabilityService.getBookedSlotCount(user.getId())
        ));
    }
}