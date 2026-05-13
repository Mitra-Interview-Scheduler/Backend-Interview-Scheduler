package com.nemal.repository;

import com.nemal.dto.InterviewScheduleDto;
import com.nemal.entity.InterviewRequest;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.domain.Pageable;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface InterviewRequestRepository extends JpaRepository<InterviewRequest, Long> {

    List<InterviewRequest> findInterviewRequestsById(Long id);
    List<InterviewRequest> findByRequestedByIdOrderByCreatedAtDesc(Long userId);

    List<InterviewRequest> findByCandidateId(Long candidateId);

    List<InterviewRequest> findByAssignedInterviewerId(Long interviewerId);

    /**
     * Upcoming interviews for an interviewer — excludes CANCELLED so the
     * interviewer dashboard immediately reflects cancellations.
     */
    @Query("SELECT r FROM InterviewRequest r " +
            "WHERE r.assignedInterviewer.id = :interviewerId " +
            "AND r.preferredStartDateTime > :now " +
            "AND r.status = 'ACCEPTED' " +
            "ORDER BY r.preferredStartDateTime ASC")
    List<InterviewRequest> findUpcomingInterviewsForInterviewer(
            @Param("interviewerId") Long interviewerId,
            @Param("now") LocalDateTime now
    );

    @Query("SELECT r FROM InterviewRequest r " +
            "WHERE r.assignedInterviewer.id = :interviewerId " +
            "AND r.preferredStartDateTime > :now " +
            "AND r.status = 'ACCEPTED' " +
            "ORDER BY r.preferredStartDateTime ASC")
    List<InterviewRequest> findUpcomingInterviewsForInterviewer(
            @Param("interviewerId") Long interviewerId,
            @Param("now") LocalDateTime now,
            Pageable pageable
    );
}