package com.nemal.repository;

import com.nemal.entity.CandidateClosure;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
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
}
