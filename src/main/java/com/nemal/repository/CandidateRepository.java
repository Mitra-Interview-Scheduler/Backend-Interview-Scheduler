package com.nemal.repository;

import com.nemal.entity.Candidate;
import com.nemal.enums.MasterStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface CandidateRepository extends JpaRepository<Candidate, Long> {

    // ── List queries ─────────────────────────────────────────────────────────

    List<Candidate> findByIsActiveTrueOrderByAppliedAtDesc();

    List<Candidate> findByDepartmentIdAndIsActiveTrueOrderByAppliedAtDesc(Long departmentId);

    List<Candidate> findByStatusAndIsActiveTrueOrderByAppliedAtDesc(MasterStatus status);

    List<Candidate> findByDepartmentIdAndStatusAndIsActiveTrueOrderByAppliedAtDesc(
            Long departmentId, MasterStatus status);

    // ── Email uniqueness — GLOBAL (active + soft-deleted) ───────────────────

    /**
     * Used on CREATE: rejects even emails from soft-deleted candidates so we
     * can safely restore them later without a collision.
     */
    boolean existsByEmail(String email);

    /**
     * Used on UPDATE: rejects emails that belong to *another* candidate
     * (active or inactive). Case-insensitive comparison.
     */
    @Query("SELECT CASE WHEN COUNT(c) > 0 THEN true ELSE false END " +
            "FROM Candidate c WHERE LOWER(c.email) = LOWER(:email) AND c.id <> :id")
    boolean existsByEmailIgnoreCaseAndIdNot(
            @Param("email") String email,
            @Param("id")    Long id);

    // ── Legacy — kept for backward compat, not used in updated service ───────

    boolean existsByEmailAndIsActiveTrue(String email);

    boolean existsByEmailAndIsActiveTrueAndIdNot(String email, Long id);

    // ── Search ────────────────────────────────────────────────────────────────

    @Query("SELECT c FROM Candidate c WHERE c.isActive = true AND " +
            "(LOWER(c.name)  LIKE LOWER(CONCAT('%', :searchTerm, '%')) OR " +
            " LOWER(c.email) LIKE LOWER(CONCAT('%', :searchTerm, '%')) OR " +
            " LOWER(COALESCE(c.resourceRequestNumber, '')) LIKE LOWER(CONCAT('%', :searchTerm, '%'))) " +
            "ORDER BY c.appliedAt DESC")
    List<Candidate> searchCandidates(@Param("searchTerm") String searchTerm);

    Optional<Candidate> findByIdAndIsActiveTrue(Long id);
}