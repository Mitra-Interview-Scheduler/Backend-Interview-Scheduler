package com.nemal.repository;

import com.nemal.entity.InterviewSchedule;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface InterviewScheduleRepository extends JpaRepository<InterviewSchedule, Long> {

    /**
     * Find the schedule linked to a specific InterviewRequest.
     * Used during cancellation so we can cancel the schedule even after
     * the slot's interviewSchedule FK has been nulled out.
     */
    Optional<InterviewSchedule> findByRequestId(Long requestId);

    @Query("""
            SELECT s FROM InterviewSchedule s
            LEFT JOIN FETCH s.request r
            LEFT JOIN FETCH r.panel
            WHERE s.id = :scheduleId
            """)
    Optional<InterviewSchedule> findByIdWithRequestAndPanel(@Param("scheduleId") Long scheduleId);

    @Query("""
            SELECT s FROM InterviewSchedule s
            JOIN s.request r
            WHERE r.panel.id = :panelId
            """)
    List<InterviewSchedule> findByPanelId(@Param("panelId") Long panelId);
}