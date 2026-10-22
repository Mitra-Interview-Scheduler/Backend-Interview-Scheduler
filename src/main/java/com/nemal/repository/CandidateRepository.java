package com.nemal.repository;

import com.nemal.entity.Candidate;
import com.nemal.enums.MasterStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface CandidateRepository extends JpaRepository<Candidate, Long> {

    // ── List queries ─────────────────────────────────────────────────────────

    List<Candidate> findByIsActiveTrueOrderByAppliedAtDesc();

    List<Candidate> findByDepartmentIdAndIsActiveTrueOrderByAppliedAtDesc(Long departmentId);

    List<Candidate> findByMasterStepStatusKeyAndIsActiveTrueOrderByAppliedAtDesc(String statusKey);

    default List<Candidate> findByStatusAndIsActiveTrueOrderByAppliedAtDesc(MasterStatus status) {
        return findByMasterStepStatusKeyAndIsActiveTrueOrderByAppliedAtDesc(status.name());
    }

    List<Candidate> findByDepartmentIdAndMasterStepStatusKeyAndIsActiveTrueOrderByAppliedAtDesc(
            Long departmentId, String statusKey);

    default List<Candidate> findByDepartmentIdAndStatusAndIsActiveTrueOrderByAppliedAtDesc(
            Long departmentId, MasterStatus status) {
        return findByDepartmentIdAndMasterStepStatusKeyAndIsActiveTrueOrderByAppliedAtDesc(
                departmentId, status.name());
    }

    // Legacy method signatures kept for repository callers — implemented via master step key.
    // findByStatusAndIsActiveTrueOrderByAppliedAtDesc uses default above.

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

    @Query("""
            SELECT c FROM Candidate c
            LEFT JOIN c.masterStep ms
            WHERE c.isActive = true
            AND (:departmentId IS NULL OR c.department.id = :departmentId)
            AND (:statusKey IS NULL OR ms.statusKey = :statusKey)
            AND (:coordinatedHrId IS NULL OR c.coordinatedHr.id = :coordinatedHrId)
            AND (:searchTerm IS NULL OR :searchTerm = '' OR
                 LOWER(c.name) LIKE LOWER(CONCAT('%', :searchTerm, '%')) OR
                 LOWER(c.email) LIKE LOWER(CONCAT('%', :searchTerm, '%')) OR
                 LOWER(COALESCE(c.resourceRequestNumber, '')) LIKE LOWER(CONCAT('%', :searchTerm, '%')))
            ORDER BY c.appliedAt DESC
            """)
    Page<Candidate> findWithFiltersPaged(
            @Param("departmentId") Long departmentId,
            @Param("statusKey") String statusKey,
            @Param("searchTerm") String searchTerm,
            @Param("coordinatedHrId") Long coordinatedHrId,
            Pageable pageable);

    Optional<Candidate> findByIdAndIsActiveTrue(Long id);

    @Query("SELECT c FROM Candidate c LEFT JOIN FETCH c.coordinatedHr LEFT JOIN FETCH c.targetDesignation WHERE c.id = :id")
    Optional<Candidate> findByIdWithCoordinatedHr(@Param("id") Long id);

    @Query("""
            SELECT c FROM Candidate c
            LEFT JOIN FETCH c.masterStep ms
            LEFT JOIN FETCH c.department
            LEFT JOIN FETCH c.targetDesignation
            LEFT JOIN FETCH c.coordinatedHr
            LEFT JOIN FETCH c.createdBy
            WHERE c.isActive = true
              AND (ms.isClosingStep = false OR ms.isClosingStep IS NULL)
              AND (:coordinatedHrId IS NULL OR c.coordinatedHr.id = :coordinatedHrId)
            ORDER BY c.updatedAt DESC
            """)
    List<Candidate> findActiveNonClosed(
            @Param("coordinatedHrId") Long coordinatedHrId);

    @Query("""
            SELECT c FROM Candidate c
            LEFT JOIN FETCH c.masterStep ms
            LEFT JOIN FETCH c.department
            LEFT JOIN FETCH c.targetDesignation
            LEFT JOIN FETCH c.coordinatedHr
            WHERE c.isActive = true
              AND ms.statusKey = :statusKey
              AND (:coordinatedHrId IS NULL OR c.coordinatedHr.id = :coordinatedHrId)
            ORDER BY c.updatedAt ASC
            """)
    List<Candidate> findActiveByStatusKey(
            @Param("statusKey") String statusKey,
            @Param("coordinatedHrId") Long coordinatedHrId);

    @Query("""
            SELECT ms.statusKey, ms.label, ms.stepOrder, COUNT(c)
            FROM Candidate c
            JOIN c.masterStep ms
            WHERE c.isActive = true
              AND (:coordinatedHrId IS NULL OR c.coordinatedHr.id = :coordinatedHrId)
            GROUP BY ms.statusKey, ms.label, ms.stepOrder
            ORDER BY ms.stepOrder
            """)
    List<Object[]> countPipelineFunnel(@Param("coordinatedHrId") Long coordinatedHrId);

    @Query("""
            SELECT COUNT(c) FROM Candidate c
            WHERE c.createdAt >= :from AND c.createdAt < :to
            """)
    long countCreatedBetween(@Param("from") LocalDateTime from, @Param("to") LocalDateTime to);

    @Query("""
            SELECT c.createdBy.id, CONCAT(c.createdBy.firstName, ' ', c.createdBy.lastName), COUNT(c)
            FROM Candidate c
            WHERE c.createdAt >= :from AND c.createdBy IS NOT NULL
            GROUP BY c.createdBy.id, c.createdBy.firstName, c.createdBy.lastName
            """)
    List<Object[]> countCreatedByUserSince(@Param("from") LocalDateTime from);

    @Query("""
            SELECT c.coordinatedHr.id, CONCAT(c.coordinatedHr.firstName, ' ', c.coordinatedHr.lastName), COUNT(c)
            FROM Candidate c
            WHERE c.isActive = true AND c.coordinatedHr IS NOT NULL
            GROUP BY c.coordinatedHr.id, c.coordinatedHr.firstName, c.coordinatedHr.lastName
            """)
    List<Object[]> countCoordinatedByHr();

    @Query("""
            SELECT c.department.id, c.department.name,
                   COUNT(c),
                   SUM(CASE WHEN ms.statusKey = 'OFFER_PENDING' OR ms.statusKey = 'SELECTED' THEN 1 ELSE 0 END),
                   SUM(CASE WHEN ms.statusKey = 'SELECTED' THEN 1 ELSE 0 END)
            FROM Candidate c
            JOIN c.masterStep ms
            LEFT JOIN c.department
            WHERE c.isActive = true AND c.department IS NOT NULL
            GROUP BY c.department.id, c.department.name
            """)
    List<Object[]> departmentFunnelCounts();

    @Query("""
            SELECT c FROM Candidate c
            LEFT JOIN FETCH c.masterStep ms
            LEFT JOIN FETCH c.department
            LEFT JOIN FETCH c.targetDesignation
            LEFT JOIN FETCH c.coordinatedHr
            WHERE c.isActive = true
              AND ms.statusKey IN :statusKeys
              AND (:coordinatedHrId IS NULL OR c.coordinatedHr.id = :coordinatedHrId)
            ORDER BY c.updatedAt ASC
            """)
    List<Candidate> findActiveByStatusKeys(
            @Param("statusKeys") Collection<String> statusKeys,
            @Param("coordinatedHrId") Long coordinatedHrId);
}