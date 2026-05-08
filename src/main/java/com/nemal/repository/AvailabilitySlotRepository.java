package com.nemal.repository;

import com.nemal.entity.AvailabilitySlot;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface AvailabilitySlotRepository extends JpaRepository<AvailabilitySlot, Long> {

    // ── Interviewer's own slots (AvailabilityService) ─────────────────────────

    /**
     * All active slots for an interviewer from a lookback point onwards.
     * The LEFT JOINs on interviewSchedule + request let AvailabilitySlotDto.from()
     * resolve candidateName without an extra query.
     * Pass now-minus-14d from AvailabilityService.
     */
    @Query("SELECT s FROM AvailabilitySlot s " +
            "LEFT JOIN FETCH s.interviewSchedule sch " +
            "LEFT JOIN FETCH sch.request " +
            "WHERE s.interviewer.id = :interviewerId " +
            "AND s.isActive = true " +
            "AND (s.status = 'AVAILABLE' OR s.startDateTime >= :from)")
    List<AvailabilitySlot> findByInterviewerIdAndIsActiveTrueWithLookback(
            @Param("interviewerId") Long interviewerId,
            @Param("from") LocalDateTime from);

    /**
     * Kept for backward compatibility — nothing should break if called elsewhere.
     */
    List<AvailabilitySlot> findByInterviewerIdAndIsActiveTrue(Long interviewerId);

    List<AvailabilitySlot> findByInterviewerIdAndStartDateTimeBetweenAndIsActiveTrue(
            Long interviewerId,
            LocalDateTime start,
            LocalDateTime end);

    // ── AVAILABLE only (conflict checks, interviewer request matching) ────────

    @Query("SELECT DISTINCT s FROM AvailabilitySlot s " +
            "LEFT JOIN FETCH s.interviewer i " +
            "LEFT JOIN FETCH i.department " +
            "LEFT JOIN FETCH i.currentDesignation d " +
            "LEFT JOIN FETCH d.tier " +
            "LEFT JOIN FETCH i.interviewerTechnologies it " +
            "LEFT JOIN FETCH it.technology " +
            "WHERE s.status = 'AVAILABLE' " +
            "AND s.isActive = true " +
            "AND s.startDateTime >= :now " +
            "ORDER BY s.startDateTime")
    List<AvailabilitySlot> findAllAvailableSlots(@Param("now") LocalDateTime now);

    @Query("SELECT DISTINCT s FROM AvailabilitySlot s " +
            "LEFT JOIN FETCH s.interviewer i " +
            "LEFT JOIN FETCH i.department " +
            "LEFT JOIN FETCH i.currentDesignation d " +
            "LEFT JOIN FETCH d.tier " +
            "LEFT JOIN FETCH i.interviewerTechnologies it " +
            "LEFT JOIN FETCH it.technology " +
            "WHERE s.status = 'AVAILABLE' " +
            "AND s.isActive = true " +
            "AND s.startDateTime >= :start " +
            "AND s.endDateTime <= :end " +
            "ORDER BY s.startDateTime")
    List<AvailabilitySlot> findAllAvailableSlotsByDateRange(
            @Param("start") LocalDateTime start,
            @Param("end") LocalDateTime end);

    // ── AVAILABLE + BOOKED for HR calendar — with lookback ───────────────────

    /**
     * Primary HR calendar query.
     * Pass now-minus-30d as :from so HR sees recent history.
     * LEFT JOINs on interviewSchedule + request let the DTO resolve
     * candidateName and requestId in one shot.
     */
    @Query("SELECT DISTINCT s FROM AvailabilitySlot s " +
            "LEFT JOIN FETCH s.interviewer i " +
            "LEFT JOIN FETCH i.department " +
            "LEFT JOIN FETCH i.currentDesignation d " +
            "LEFT JOIN FETCH d.tier " +
            "LEFT JOIN FETCH i.interviewerTechnologies it " +
            "LEFT JOIN FETCH it.technology " +
            "LEFT JOIN FETCH s.interviewSchedule sch " +
            "LEFT JOIN FETCH sch.request " +
            "WHERE s.isActive = true " +
            "AND (s.status = 'AVAILABLE' OR s.status = 'BOOKED') " +
            "AND s.startDateTime >= :from " +
            "ORDER BY s.startDateTime")
    List<AvailabilitySlot> findAllActiveSlotsForHR(@Param("from") LocalDateTime from);

    /**
     * HR calendar with explicit date-range filter applied from the UI.
     */
    @Query("SELECT DISTINCT s FROM AvailabilitySlot s " +
            "LEFT JOIN FETCH s.interviewer i " +
            "LEFT JOIN FETCH i.department " +
            "LEFT JOIN FETCH i.currentDesignation d " +
            "LEFT JOIN FETCH d.tier " +
            "LEFT JOIN FETCH i.interviewerTechnologies it " +
            "LEFT JOIN FETCH it.technology " +
            "LEFT JOIN FETCH s.interviewSchedule sch " +
            "LEFT JOIN FETCH sch.request " +
            "WHERE s.isActive = true " +
            "AND (s.status = 'AVAILABLE' OR s.status = 'BOOKED') " +
            "AND s.startDateTime >= :start " +
            "AND s.endDateTime <= :end " +
            "ORDER BY s.startDateTime")
    List<AvailabilitySlot> findAllActiveSlotsForHRByDateRange(
            @Param("start") LocalDateTime start,
            @Param("end") LocalDateTime end);

    // ── Conflict detection ────────────────────────────────────────────────────

    @Query("SELECT s FROM AvailabilitySlot s " +
            "WHERE s.interviewer.id = :interviewerId " +
            "AND s.isActive = true " +
            "AND s.status = 'AVAILABLE' " +
            "AND s.startDateTime < :end AND s.endDateTime > :start")
    List<AvailabilitySlot> findConflictingSlots(
            @Param("interviewerId") Long interviewerId,
            @Param("start") LocalDateTime start,
            @Param("end") LocalDateTime end);

    // ── Stats ─────────────────────────────────────────────────────────────────

    @Query("SELECT COUNT(s) FROM AvailabilitySlot s " +
            "WHERE s.interviewer.id = :interviewerId " +
            "AND s.isActive = true AND s.status = 'AVAILABLE' AND s.startDateTime >= :from")
    long countAvailableSlotsFrom(@Param("interviewerId") Long interviewerId,
                                 @Param("from") LocalDateTime from);

    @Query("SELECT COUNT(s) FROM AvailabilitySlot s " +
            "WHERE s.interviewer.id = :interviewerId " +
            "AND s.isActive = true AND s.status = 'BOOKED' AND s.startDateTime >= :from")
    long countBookedSlotsFrom(@Param("interviewerId") Long interviewerId,
                              @Param("from") LocalDateTime from);

    // Backward-compat aliases kept so no call sites break
    default long countUpcomingAvailableSlots(Long interviewerId, LocalDateTime now) {
        return countAvailableSlotsFrom(interviewerId, now);
    }

    default long countUpcomingBookedSlots(Long interviewerId, LocalDateTime now) {
        return countBookedSlotsFrom(interviewerId, now);
    }

    // ── NEW: Adjacent-slot queries for merge-on-cancel ────────────────────────

    /**
     * Find an ACTIVE + AVAILABLE slot whose endDateTime exactly equals {@code time}
     * for the given interviewer — the "before" fragment left when a slot was split.
     * Called by InterviewRequestService and PanelInterviewService after restoring
     * a cancelled slot so the fragments can be merged back into one window.
     */
    @Query("SELECT s FROM AvailabilitySlot s " +
            "WHERE s.interviewer.id = :interviewerId " +
            "AND s.isActive = true " +
            "AND s.status = 'AVAILABLE' " +
            "AND s.endDateTime = :time")
    Optional<AvailabilitySlot> findActiveAvailableSlotEndingAt(
            @Param("interviewerId") Long interviewerId,
            @Param("time") LocalDateTime time);

    /**
     * Find an ACTIVE + AVAILABLE slot whose startDateTime exactly equals {@code time}
     * for the given interviewer — the "after" fragment left when a slot was split.
     */
    @Query("SELECT s FROM AvailabilitySlot s " +
            "WHERE s.interviewer.id = :interviewerId " +
            "AND s.isActive = true " +
            "AND s.status = 'AVAILABLE' " +
            "AND s.startDateTime = :time")
    Optional<AvailabilitySlot> findActiveAvailableSlotStartingAt(
            @Param("interviewerId") Long interviewerId,
            @Param("time") LocalDateTime time);
}