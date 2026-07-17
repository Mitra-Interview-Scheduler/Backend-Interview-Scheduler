package com.nemal.controller;

import com.nemal.dto.InterviewRequestDto;
import com.nemal.dto.InterviewRequestSimpleDto;
import com.nemal.entity.InterviewRequest;
import com.nemal.entity.User;
import com.nemal.service.InterviewRequestService;
import com.nemal.util.TimeZoneMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/interviewer/interviews")
@CrossOrigin(origins = "http://localhost:5173")
public class InterviewerBookedInterviewController {

    private static final Logger logger = LoggerFactory.getLogger(InterviewRequestController.class);
    private final InterviewRequestService interviewRequestService;


    public InterviewerBookedInterviewController(InterviewRequestService interviewRequestService) {
        this.interviewRequestService = interviewRequestService;
    }

    @GetMapping("/bookedInterviews/{interviewScheduleId}")
    @Transactional(readOnly = true)
    public ResponseEntity<?> getMyBookedInterview(@PathVariable Long interviewScheduleId)
    {
        try {
            List<InterviewRequest> requests = interviewRequestService.getBookedInterviewSchedule(interviewScheduleId);
            List<InterviewRequestSimpleDto> dtos = requests.stream()
                    .map(request -> {
                        InterviewRequestSimpleDto dto = InterviewRequestSimpleDto.from(request);
                        var effectiveStatus = interviewRequestService.resolveEffectiveInterviewStatus(
                                request.getInterviewSchedule());
                        if (effectiveStatus != null) {
                            dto.setInterviewStatus(effectiveStatus);
                        }
                        return dto;
                    })
                    .collect(Collectors.toList());
            return ResponseEntity.status(HttpStatus.OK).body(dtos);
        } catch (Exception e) {
            logger.error("Failed to get requests for Booked Slot If {}: {}", interviewScheduleId, e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("message", e.getMessage()));
        }

    }

    @PatchMapping("/schedules/{scheduleId}/complete")
    public ResponseEntity<?> completeInterview(
            @AuthenticationPrincipal User user,
            @PathVariable Long scheduleId,
            @RequestHeader(value = "X-Timezone", required = false) String timezone) {
        try {
            ZoneId zone = TimeZoneMapper.resolveZone(timezone);
            InterviewRequestDto result = interviewRequestService.completeInterview(user, scheduleId);
            return ResponseEntity.ok(TimeZoneMapper.fromUtc(result, zone));
        } catch (Exception e) {
            logger.error("Failed to complete interview schedule {}: {}", scheduleId, e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of("message", e.getMessage()));
        }
    }
}









