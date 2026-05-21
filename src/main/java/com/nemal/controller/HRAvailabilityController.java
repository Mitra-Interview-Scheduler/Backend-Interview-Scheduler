package com.nemal.controller;

import com.nemal.dto.AvailabilityFilterDto;
import com.nemal.dto.InterviewerAvailabilityDto;
import com.nemal.service.HRAvailabilityService;
import com.nemal.util.TimeZoneMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.ZoneId;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/hr/availability")
@CrossOrigin(origins = "http://localhost:5173")
public class HRAvailabilityController {

    private static final Logger logger = LoggerFactory.getLogger(HRAvailabilityController.class);
    private final HRAvailabilityService hrAvailabilityService;

    public HRAvailabilityController(HRAvailabilityService hrAvailabilityService) {
        this.hrAvailabilityService = hrAvailabilityService;
    }

    @PostMapping("/filter")
    public ResponseEntity<?> getFilteredAvailability(
            @RequestBody(required = false) AvailabilityFilterDto filter,
            @RequestHeader(value = "X-Timezone", required = false) String timezone
    ) {
        try {
            logger.info("Received filter request: {}", filter);
            ZoneId zone = TimeZoneMapper.resolveZone(timezone);
                    AvailabilityFilterDto utcFilter = filter == null ? null : new AvailabilityFilterDto(
                        filter.departmentIds(),
                        filter.technologyIds(),
                        TimeZoneMapper.toUtc(filter.startDateTime(), zone),
                        TimeZoneMapper.toUtc(filter.endDateTime(), zone),
                        filter.minYearsOfExperience(),
                        filter.minDesignationLevelInDepartment(),
                        filter.departmentIdForDesignationFilter(),
                        filter.minTierId(),
                        filter.page(),
                        filter.size()
                    );

                    if (utcFilter != null && utcFilter.page() != null && utcFilter.size() != null) {
                    var paged = hrAvailabilityService.getAllAvailableSlotsPaged(utcFilter);
                    logger.info("Returning paged availability slots page {} size {} total {}", paged.page(), paged.size(), paged.total());
                    return ResponseEntity.ok(Map.of(
                        "items", TimeZoneMapper.fromUtcInterviewerAvailability(paged.items(), zone),
                        "total", paged.total(),
                        "page", paged.page(),
                        "size", paged.size()
                    ));
                    }

                    List<InterviewerAvailabilityDto> fullResult = hrAvailabilityService.getAllAvailableSlots(utcFilter);
                    logger.info("Returning {} availability slots", fullResult.size());
                    return ResponseEntity.ok(TimeZoneMapper.fromUtcInterviewerAvailability(fullResult, zone));
        } catch (Exception e) {
            logger.error("Error in getFilteredAvailability: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of(
                            "error", "Failed to fetch availability",
                            "message", e.getMessage(),
                            "timestamp", System.currentTimeMillis()
                    ));
        }
    }

    @GetMapping
    public ResponseEntity<?> getAllAvailability(
            @RequestHeader(value = "X-Timezone", required = false) String timezone) {
        try {
            logger.info("Received request for all availability");
            ZoneId zone = TimeZoneMapper.resolveZone(timezone);
            List<InterviewerAvailabilityDto> result = hrAvailabilityService.getAllAvailableSlots(null);
            logger.info("Returning {} availability slots", result.size());
            return ResponseEntity.ok(TimeZoneMapper.fromUtcInterviewerAvailability(result, zone));
        } catch (Exception e) {
            logger.error("Error in getAllAvailability: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of(
                            "error", "Failed to fetch availability",
                            "message", e.getMessage(),
                            "timestamp", System.currentTimeMillis()
                    ));
        }
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<?> handleException(Exception e) {
        logger.error("Unhandled exception in HRAvailabilityController: {}", e.getMessage(), e);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of(
                        "error", "Internal server error",
                        "message", e.getMessage(),
                        "timestamp", System.currentTimeMillis()
                ));
    }
}