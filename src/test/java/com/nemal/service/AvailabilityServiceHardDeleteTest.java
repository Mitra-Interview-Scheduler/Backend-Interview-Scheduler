package com.nemal.service;

import com.nemal.entity.AvailabilitySlot;
import com.nemal.entity.User;
import com.nemal.enums.SlotStatus;
import com.nemal.repository.AvailabilitySlotRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AvailabilityServiceHardDeleteTest {

    @Mock
    private AvailabilitySlotRepository availabilitySlotRepository;
    @Mock
    private InterviewRequestService interviewRequestService;
    @Mock
    private InterviewPostponeRequestService postponeRequestService;
    @Mock
    private CalendarSyncService calendarSyncService;

    private AvailabilityService availabilityService;

    @BeforeEach
    void setUp() {
        availabilityService = new AvailabilityService(
                availabilitySlotRepository,
                interviewRequestService,
                postponeRequestService,
                calendarSyncService);
    }

    @Test
    void deleteAvailabilitySlot_hardDeletesRow() {
        User interviewer = User.builder().id(1L).email("interviewer@company.com").build();
        AvailabilitySlot slot = AvailabilitySlot.builder()
                .id(42L)
                .interviewer(interviewer)
                .status(SlotStatus.AVAILABLE)
                .startDateTime(LocalDateTime.of(2026, 7, 15, 10, 0))
                .endDateTime(LocalDateTime.of(2026, 7, 15, 11, 0))
                .isActive(true)
                .build();

        when(availabilitySlotRepository.findById(42L)).thenReturn(Optional.of(slot));

        availabilityService.deleteAvailabilitySlot(interviewer, 42L, "SINGLE");

        verify(calendarSyncService).syncAvailabilitySlotDeleted(slot);
        verify(availabilitySlotRepository).delete(slot);
    }
}
