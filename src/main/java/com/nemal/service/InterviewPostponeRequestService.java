package com.nemal.service;



import com.nemal.dto.ApproveInterviewPostponeRequestDto;

import com.nemal.dto.CreateInterviewPostponeRequestDto;

import com.nemal.dto.CreateInterviewRequestDto;

import com.nemal.dto.InterviewPostponeRequestDto;

import com.nemal.dto.InterviewRequestDto;

import com.nemal.dto.RejectInterviewPostponeRequestDto;

import com.nemal.entity.AvailabilitySlot;

import com.nemal.entity.InterviewPostponeRequest;

import com.nemal.entity.InterviewRequest;

import com.nemal.entity.InterviewSchedule;

import com.nemal.entity.Technology;

import com.nemal.entity.User;

import com.nemal.enums.InterviewStatus;

import com.nemal.enums.PostponeRequestStatus;

import com.nemal.enums.Role;

import com.nemal.repository.AvailabilitySlotRepository;

import com.nemal.repository.InterviewPostponeRequestRepository;

import com.nemal.repository.InterviewScheduleRepository;

import org.springframework.stereotype.Service;

import org.springframework.transaction.annotation.Transactional;



import java.time.LocalDateTime;

import java.util.Collection;

import java.util.List;

import java.util.Map;

import java.util.Objects;

import java.util.stream.Collectors;



@Service

public class InterviewPostponeRequestService {



    private final InterviewPostponeRequestRepository postponeRequestRepository;

    private final InterviewScheduleRepository interviewScheduleRepository;

    private final AvailabilitySlotRepository availabilitySlotRepository;

    private final InterviewRequestService interviewRequestService;

    private final NotificationService notificationService;



    public InterviewPostponeRequestService(

            InterviewPostponeRequestRepository postponeRequestRepository,

            InterviewScheduleRepository interviewScheduleRepository,

            AvailabilitySlotRepository availabilitySlotRepository,

            InterviewRequestService interviewRequestService,

            NotificationService notificationService) {

        this.postponeRequestRepository = postponeRequestRepository;

        this.interviewScheduleRepository = interviewScheduleRepository;

        this.availabilitySlotRepository = availabilitySlotRepository;

        this.interviewRequestService = interviewRequestService;

        this.notificationService = notificationService;

    }



    @Transactional

    public InterviewPostponeRequestDto createPostponeRequest(

            User interviewer,

            Long scheduleId,

            CreateInterviewPostponeRequestDto dto) {

        InterviewSchedule schedule = interviewScheduleRepository.findByIdWithPostponeDetails(scheduleId)

                .orElseThrow(() -> new RuntimeException("Interview schedule not found: " + scheduleId));



        assertInterviewerCanRequest(interviewer, schedule);

        assertScheduleCanBePostponed(schedule);



        if (postponeRequestRepository.findByInterviewScheduleIdAndStatus(

                scheduleId, PostponeRequestStatus.PENDING).isPresent()) {

            throw new RuntimeException("A postpone request is already pending for this interview");

        }



        String reason = dto.reason() != null ? dto.reason().trim() : "";

        if (reason.isBlank()) {

            reason = "Interviewer proposed an alternative time for this scheduled interview.";

        }

        if (reason.length() > 2000) {

            throw new RuntimeException("Reason must be at most 2000 characters");

        }



        if (dto.preferredStartDateTime() == null || dto.preferredEndDateTime() == null) {

            throw new RuntimeException("A proposed start and end time are required");

        }

        validatePreferredWindow(dto.preferredStartDateTime(), dto.preferredEndDateTime());



        InterviewRequest request = schedule.getRequest();

        if (request == null) {

            throw new RuntimeException("Interview request not found for schedule: " + scheduleId);

        }

        if (request.getPanel() != null) {

            throw new RuntimeException("Panel interviews cannot use propose alternative time yet");

        }



        InterviewPostponeRequest postponeRequest = InterviewPostponeRequest.builder()

                .interviewSchedule(schedule)

                .interviewRequest(request)

                .requestedBy(interviewer)

                .reason(reason)

                .preferredStartDateTime(dto.preferredStartDateTime())

                .preferredEndDateTime(dto.preferredEndDateTime())

                .status(PostponeRequestStatus.PENDING)

                .build();



        InterviewPostponeRequest saved = postponeRequestRepository.save(postponeRequest);

        InterviewPostponeRequest forNotify = postponeRequestRepository.findByIdWithDetails(saved.getId())

                .orElse(saved);

        notificationService.sendInterviewPostponeRequestedNotification(forNotify);



        return InterviewPostponeRequestDto.from(saved);

    }



    @Transactional(readOnly = true)

    public InterviewPostponeRequestDto getPendingForSchedule(Long scheduleId) {

        return postponeRequestRepository

                .findPendingByScheduleIdWithDetails(scheduleId, PostponeRequestStatus.PENDING)

                .map(InterviewPostponeRequestDto::from)

                .orElse(null);

    }



    @Transactional(readOnly = true)

    public List<InterviewPostponeRequestDto> getHistoryForSchedule(Long scheduleId) {

        return postponeRequestRepository.findByInterviewScheduleIdOrderByCreatedAtDesc(scheduleId)

                .stream()

                .map(InterviewPostponeRequestDto::from)

                .toList();

    }



    @Transactional(readOnly = true)

    public List<InterviewPostponeRequestDto> listPendingRequests() {

        return postponeRequestRepository.findByStatusOrderByCreatedAtDesc(PostponeRequestStatus.PENDING)

                .stream()

                .map(InterviewPostponeRequestDto::from)

                .toList();

    }



    @Transactional(readOnly = true)

    public long countPendingRequests() {

        return postponeRequestRepository.countByStatus(PostponeRequestStatus.PENDING);

    }



    @Transactional(readOnly = true)

    public Map<Long, InterviewPostponeRequestDto> findPendingByScheduleIds(Collection<Long> scheduleIds) {

        if (scheduleIds == null || scheduleIds.isEmpty()) {

            return Map.of();

        }

        return postponeRequestRepository

                .findByInterviewScheduleIdInAndStatus(scheduleIds, PostponeRequestStatus.PENDING)

                .stream()

                .filter(request -> request.getInterviewSchedule() != null)

                .collect(Collectors.toMap(

                        request -> request.getInterviewSchedule().getId(),

                        InterviewPostponeRequestDto::from,

                        (left, right) -> left));

    }



    @Transactional

    public InterviewPostponeRequestDto withdrawPostponeRequest(User interviewer, Long postponeRequestId) {

        InterviewPostponeRequest request = postponeRequestRepository.findByIdWithDetails(postponeRequestId)

                .orElseThrow(() -> new RuntimeException("Postpone request not found: " + postponeRequestId));



        if (request.getStatus() != PostponeRequestStatus.PENDING) {

            throw new RuntimeException("Only pending postpone requests can be withdrawn");

        }

        if (request.getRequestedBy() == null

                || !Objects.equals(request.getRequestedBy().getId(), interviewer.getId())) {

            throw new RuntimeException("You can only withdraw your own postpone request");

        }



        request.setStatus(PostponeRequestStatus.WITHDRAWN);

        request.setResolvedAt(LocalDateTime.now());

        InterviewPostponeRequest saved = postponeRequestRepository.save(request);

        return InterviewPostponeRequestDto.from(saved);

    }



    @Transactional

    public InterviewPostponeRequestDto rejectPostponeRequest(

            User hrUser,

            Long postponeRequestId,

            RejectInterviewPostponeRequestDto dto) {

        assertHrOrAdmin(hrUser);



        InterviewPostponeRequest request = postponeRequestRepository.findByIdWithDetails(postponeRequestId)

                .orElseThrow(() -> new RuntimeException("Postpone request not found: " + postponeRequestId));



        if (request.getStatus() != PostponeRequestStatus.PENDING) {

            throw new RuntimeException("Only pending postpone requests can be rejected");

        }



        request.setStatus(PostponeRequestStatus.REJECTED);

        request.setReviewedBy(hrUser);

        request.setReviewNotes(dto != null && dto.reviewNotes() != null ? dto.reviewNotes().trim() : null);

        request.setResolvedAt(LocalDateTime.now());



        InterviewPostponeRequest saved = postponeRequestRepository.save(request);

        notificationService.sendInterviewPostponeRejectedNotification(saved);

        return InterviewPostponeRequestDto.from(saved);

    }



    /**

     * Approves a pending postpone: cancels the current interview and books the

     * interviewer's proposed time on a covering available slot.

     */

    @Transactional

    public InterviewPostponeRequestDto approvePostponeRequest(

            User hrUser,

            Long postponeRequestId,

            ApproveInterviewPostponeRequestDto dto) {

        assertHrOrAdmin(hrUser);



        InterviewPostponeRequest postpone = postponeRequestRepository.findByIdWithDetails(postponeRequestId)

                .orElseThrow(() -> new RuntimeException("Postpone request not found: " + postponeRequestId));



        if (postpone.getStatus() != PostponeRequestStatus.PENDING) {

            throw new RuntimeException("Only pending postpone requests can be approved");

        }



        InterviewSchedule oldSchedule = postpone.getInterviewSchedule();

        InterviewRequest oldRequest = postpone.getInterviewRequest();

        if (oldRequest == null && oldSchedule != null) {

            oldRequest = oldSchedule.getRequest();

        }

        if (oldSchedule == null || oldRequest == null) {

            throw new RuntimeException("Postpone request is missing interview details");

        }

        if (oldRequest.getPanel() != null) {

            throw new RuntimeException("Panel interviews cannot be rescheduled via postpone approval");

        }

        if (oldSchedule.getStatus() != InterviewStatus.SCHEDULED) {

            throw new RuntimeException("The original interview is no longer scheduled");

        }

        if (oldRequest.getId() == null) {

            throw new RuntimeException("Original interview request id is missing");

        }



        LocalDateTime preferredStart = postpone.getPreferredStartDateTime();

        LocalDateTime preferredEnd = postpone.getPreferredEndDateTime();

        validatePreferredWindow(preferredStart, preferredEnd);

        if (!preferredStart.isAfter(LocalDateTime.now())) {

            throw new RuntimeException("The proposed time is in the past and cannot be accepted");

        }



        User interviewer = oldSchedule.getInterviewer() != null

                ? oldSchedule.getInterviewer()

                : oldRequest.getAssignedInterviewer();

        if (interviewer == null || interviewer.getId() == null) {

            throw new RuntimeException("Interviewer not found for this interview");

        }



        List<AvailabilitySlot> covering = availabilitySlotRepository.findCoveringAvailableSlots(

                interviewer.getId(), preferredStart, preferredEnd);

        if (covering.isEmpty()) {

            throw new RuntimeException(

                    "No available slot covers the proposed time. Ask the interviewer to propose another slot.");

        }

        AvailabilitySlot targetSlot = covering.get(0);



        List<Long> technologyIds = oldRequest.getRequiredTechnologies() == null

                ? List.of()

                : oldRequest.getRequiredTechnologies().stream()

                        .filter(Objects::nonNull)

                        .map(Technology::getId)

                        .filter(Objects::nonNull)

                        .toList();



        String interviewType = oldSchedule.getInterviewType();



        Long coordinatorId = oldRequest.getInterviewCoordinator() != null

                ? oldRequest.getInterviewCoordinator().getId()

                : null;



        String notes = "Rescheduled after accepting postpone request #" + postpone.getId();

        if (oldRequest.getNotes() != null && !oldRequest.getNotes().isBlank()) {

            notes = oldRequest.getNotes().trim() + " | " + notes;

        }



        CreateInterviewRequestDto createDto = new CreateInterviewRequestDto(

                oldRequest.getCandidate() != null ? oldRequest.getCandidate().getId() : null,

                oldRequest.getCandidateName(),

                oldRequest.getCandidateInviteEmail(),

                oldRequest.getCandidateDesignation() != null

                        ? oldRequest.getCandidateDesignation().getId()

                        : null,

                technologyIds,

                targetSlot.getId(),

                preferredStart,

                preferredEnd,

                oldRequest.isUrgent(),

                notes,

                interviewType,

                coordinatorId,

                null,

                dto != null ? dto.acknowledgeCalendarConflict() : null

        );



        postpone.setStatus(PostponeRequestStatus.APPROVED);

        postpone.setReviewedBy(hrUser);

        postpone.setReviewNotes(dto != null && dto.reviewNotes() != null ? dto.reviewNotes().trim() : null);

        postpone.setResolvedAt(LocalDateTime.now());

        postponeRequestRepository.save(postpone);



        interviewRequestService.cancelRequest(hrUser, oldRequest.getId(), true);

        InterviewRequestDto newInterview = interviewRequestService.createInterviewRequest(hrUser, createDto);



        InterviewPostponeRequest saved = postponeRequestRepository.findByIdWithDetails(postponeRequestId)

                .orElse(postpone);

        try {

            notificationService.sendInterviewPostponeApprovedNotification(saved, newInterview);

        } catch (Exception ignored) {

            // booking already succeeded

        }

        return InterviewPostponeRequestDto.from(saved);

    }



    private void assertHrOrAdmin(User user) {

        if (user == null || user.getRoles() == null

                || !(user.getRoles().contains(Role.HR) || user.getRoles().contains(Role.ADMIN))) {

            throw new RuntimeException("Only HR or Admin can review postpone requests");

        }

    }



    private void assertInterviewerCanRequest(User interviewer, InterviewSchedule schedule) {

        if (schedule.getInterviewer() == null

                || !Objects.equals(schedule.getInterviewer().getId(), interviewer.getId())) {

            throw new RuntimeException("Only the assigned interviewer can request to postpone this interview");

        }

    }



    private void assertScheduleCanBePostponed(InterviewSchedule schedule) {

        if (schedule.getStatus() == InterviewStatus.COMPLETED) {

            throw new RuntimeException("Completed interviews cannot be postponed");

        }

        if (schedule.getStatus() == InterviewStatus.CANCELLED) {

            throw new RuntimeException("Cancelled interviews cannot be postponed");

        }

        if (schedule.getStatus() != InterviewStatus.SCHEDULED) {

            throw new RuntimeException("Only scheduled interviews can be postponed");

        }

    }



    private void validatePreferredWindow(LocalDateTime preferredStart, LocalDateTime preferredEnd) {

        if (preferredStart == null || preferredEnd == null) {

            throw new RuntimeException("Both preferred start and end times are required when suggesting a new time");

        }

        if (!preferredEnd.isAfter(preferredStart)) {

            throw new RuntimeException("Preferred end time must be after preferred start time");

        }

    }

}


