package com.nemal.controller;

import com.nemal.dto.InterviewRequestSimpleDto;
import com.nemal.entity.InterviewRequest;
import com.nemal.service.InterviewRequestService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

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
                    .map(InterviewRequestSimpleDto::from)
                    .collect(Collectors.toList());
            return ResponseEntity.status(HttpStatus.OK).body(dtos);
        } catch (Exception e) {
            logger.error("Failed to get requests for Booked Slot If {}: {}", interviewScheduleId, e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("message", e.getMessage()));
        }

    }
}









