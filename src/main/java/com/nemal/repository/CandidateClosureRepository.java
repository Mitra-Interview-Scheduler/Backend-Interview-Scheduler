package com.nemal.repository;

import com.nemal.entity.CandidateClosure;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface CandidateClosureRepository extends JpaRepository<CandidateClosure, Long> {
    Optional<CandidateClosure> findTopByCandidateIdOrderByClosedAtDesc(Long candidateId);

    @Query("""
            SELECT c FROM CandidateClosure c
            LEFT JOIN FETCH c.closingReason
            LEFT JOIN FETCH c.candidate cand
            WHERE c.closedAt >= :from
            ORDER BY c.closedAt DESC
            """)
    List<CandidateClosure> findByClosedAtAfter(@Param("from") LocalDateTime from);

    @Query("""
            SELECT cr.code, cr.label, COUNT(c)
            FROM CandidateClosure c
            JOIN c.closingReason cr
            WHERE c.closedAt >= :from
            GROUP BY cr.code, cr.label
            ORDER BY COUNT(c) DESC
            """)
    List<Object[]> countByCloseReasonSince(@Param("from") LocalDateTime from);

    /** (candidateId, closedStatusKey) for closures since {@code from}. */
    @Query("""
            SELECT c.candidate.id, c.closedStatusKey
            FROM CandidateClosure c
            WHERE c.closedAt >= :from
            """)
    List<Object[]> findCandidateStatusClosedSince(@Param("from") LocalDateTime from);

    /** Of the given candidates, those closed with {@code closedStatusKey} (at any time). */
    @Query("""
            SELECT DISTINCT c.candidate.id
            FROM CandidateClosure c
            WHERE c.closedStatusKey = :closedStatusKey
              AND c.candidate.id IN :candidateIds
            """)
    List<Long> findCandidateIdsClosedWithStatus(
            @Param("closedStatusKey") String closedStatusKey,
            @Param("candidateIds") Collection<Long> candidateIds);
}
