package com.nemal.service;

import com.nemal.dto.CreateInterviewRequestDto;
import com.nemal.dto.GoogleCalendarExternalEventDto;
import com.nemal.entity.AvailabilitySlot;
import com.nemal.entity.Candidate;
import com.nemal.entity.InterviewRequest;
import com.nemal.entity.InterviewSchedule;
import com.nemal.entity.User;
import com.nemal.enums.SlotStatus;
import com.nemal.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class InterviewRequestServiceCalendarValidationTest {

    @Mock private InterviewRequestRepository interviewRequestRepository;
    @Mock private AvailabilitySlotRepository availabilitySlotRepository;
    @Mock private InterviewScheduleRepository interviewScheduleRepository;
    @Mock private CandidateRepository candidateRepository;
    @Mock private DesignationRepository designationRepository;
    @Mock private TechnologyRepository technologyRepository;
    @Mock private TierRepository tierRepository;
    @Mock private NotificationService notificationService;
    @Mock private CandidateStepPipelineService candidateStepPipelineService;
    @Mock private FeedbackResponseRepository feedbackResponseRepository;
    @Mock private InterviewPanelRepository interviewPanelRepository;
    @Mock private MasterStepService masterStepService;
    @Mock private UserRepository userRepository;
    @Mock private CandidatePipelineAuditService candidatePipelineAuditService;
    @Mock private CalendarSyncService calendarSyncService;
    @Mock private PanelInterviewService panelInterviewService;

    private InterviewRequestService interviewRequestService;

    @BeforeEach
    void setUp() {
        interviewRequestService = new InterviewRequestService(
                interviewRequestRepository,
                availabilitySlotRepository,
                interviewScheduleRepository,
                candidateRepository,
                designationRepository,
                technologyRepository,
                tierRepository,
                notificationService,
                candidateStepPipelineService,
                feedbackResponseRepository,
                interviewPanelRepository,
                masterStepService,
                userRepository,
                candidatePipelineAuditService,
                calendarSyncService,
                panelInterviewService
        );
    }

    @Test
    void createInterviewRequest_storesInviteEmailFromDtoWithoutProfileEmail() {
        LocalDateTime start = LocalDateTime.of(2026, 7, 10, 10, 0);
        LocalDateTime end = LocalDateTime.of(2026, 7, 10, 11, 0);

        AvailabilitySlot slot = AvailabilitySlot.builder()
                .id(10L)
                .status(SlotStatus.AVAILABLE)
                .startDateTime(start)
                .endDateTime(end)
                .interviewer(User.builder().id(5L).email("interviewer@company.com").build())
                .isActive(true)
                .build();

        Candidate candidate = Candidate.builder()
                .id(1L)
                .name("Jane Doe")
                .email(" ")
                .build();

        when(availabilitySlotRepository.findById(10L)).thenReturn(Optional.of(slot));
        when(candidateRepository.findById(1L)).thenReturn(Optional.of(candidate));
        when(availabilitySlotRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(interviewRequestRepository.save(any())).thenAnswer(invocation -> {
            InterviewRequest request = invocation.getArgument(0);
            request.setId(100L);
            return request;
        });
        when(interviewScheduleRepository.save(any())).thenAnswer(invocation -> {
            InterviewSchedule schedule = invocation.getArgument(0);
            schedule.setId(200L);
            return schedule;
        });

        CreateInterviewRequestDto dto = new CreateInterviewRequestDto(
                1L,
                "Jane Doe",
                "guest@gmail.com",
                null,
                List.of(),
                10L,
                start,
                end,
                false,
                null,
                "TECHNICAL",
                null,
                null
        );

        interviewRequestService.createInterviewRequest(User.builder().id(99L).build(), dto);

        ArgumentCaptor<InterviewRequest> captor = ArgumentCaptor.forClass(InterviewRequest.class);
        verify(interviewRequestRepository).save(captor.capture());
        assertEquals("guest@gmail.com", captor.getValue().getCandidateInviteEmail());
    }

    @Test
    void createInterviewRequest_blocksWhenGoogleCalendarConflictExists() {
        LocalDateTime start = LocalDateTime.of(2026, 7, 10, 10, 0);
        LocalDateTime end = LocalDateTime.of(2026, 7, 10, 11, 0);

        User interviewer = User.builder().id(5L).firstName("Alex").lastName("Interviewer").email("interviewer@company.com").build();
        AvailabilitySlot slot = AvailabilitySlot.builder()
                .id(10L)
                .status(SlotStatus.AVAILABLE)
                .startDateTime(start)
                .endDateTime(end)
                .interviewer(interviewer)
                .isActive(true)
                .build();

        when(availabilitySlotRepository.findById(10L)).thenReturn(Optional.of(slot));
        when(userRepository.findById(5L)).thenReturn(Optional.of(interviewer));
        when(calendarSyncService.listExternalGoogleCalendarEvents(eq(interviewer), eq(start), eq(end)))
                .thenReturn(List.of(new GoogleCalendarExternalEventDto(
                        "evt-1",
                        "Team standup",
                        start.plusMinutes(15),
                        end.minusMinutes(15),
                        false,
                        true,
                        "Work Calendar"
                )));

        CreateInterviewRequestDto dto = new CreateInterviewRequestDto(
                null,
                "Jane Doe",
                null,
                null,
                List.of(),
                10L,
                start,
                end,
                false,
                null,
                "TECHNICAL",
                null,
                null
        );

        RuntimeException ex = assertThrows(RuntimeException.class,
                () -> interviewRequestService.createInterviewRequest(User.builder().id(99L).build(), dto));
        assertEquals(true, ex.getMessage().contains("Google Calendar conflict"));
        assertEquals(true, ex.getMessage().contains("Team standup"));
    }
}
