package com.nemal.repository;

import com.nemal.entity.CandidateStepPipeline;
import com.nemal.enums.PipelineStepStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface CandidateStepPipelineRepository extends JpaRepository<CandidateStepPipeline, Long> {

    List<CandidateStepPipeline> findByCandidateIdOrderBySequenceOrderAsc(Long candidateId);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE CandidateStepPipeline cps SET cps.stepStatus = :status " +
            "WHERE cps.candidate.id = :candidateId AND cps.sequenceOrder = :sequenceOrder")
    int updateStepStatus(@Param("candidateId") Long candidateId,
                         @Param("sequenceOrder") Integer sequenceOrder,
                         @Param("status") PipelineStepStatus status);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE CandidateStepPipeline cps SET cps.stepStatus = :status " +
            "WHERE cps.candidate.id = :candidateId AND cps.sequenceOrder < :sequenceOrder")
    int updateStepStatusBefore(@Param("candidateId") Long candidateId,
                               @Param("sequenceOrder") Integer sequenceOrder,
                               @Param("status") PipelineStepStatus status);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE CandidateStepPipeline cps SET cps.stepStatus = :status " +
            "WHERE cps.candidate.id = :candidateId AND cps.sequenceOrder > :sequenceOrder")
    int updateStepStatusAfter(@Param("candidateId") Long candidateId,
                              @Param("sequenceOrder") Integer sequenceOrder,
                              @Param("status") PipelineStepStatus status);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE CandidateStepPipeline cps SET cps.stepStatus = 'SKIPPED' " +
            "WHERE cps.candidate.id = :candidateId " +
            "AND cps.sequenceOrder > :currentSequenceOrder " +
            "AND cps.stepStatus = 'PENDING'")
    int skipUpcomingSteps(@Param("candidateId") Long candidateId,
                          @Param("currentSequenceOrder") Integer currentSequenceOrder);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(value = """
            UPDATE candidate_pipeline_steps
            SET sequence_order = sequence_order + :tempOffset
            WHERE candidate_id = :candidateId AND sequence_order >= :targetSequenceOrder
            """, nativeQuery = true)
    int bumpSequenceOrdersForShift(@Param("candidateId") Long candidateId,
                                   @Param("targetSequenceOrder") Integer targetSequenceOrder,
                                   @Param("tempOffset") int tempOffset);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(value = """
            UPDATE candidate_pipeline_steps
            SET sequence_order = sequence_order - :normalizeBy
            WHERE candidate_id = :candidateId
              AND sequence_order >= :targetSequenceOrder + :tempOffset
            """, nativeQuery = true)
    int normalizeSequenceOrdersAfterShift(@Param("candidateId") Long candidateId,
                                          @Param("targetSequenceOrder") Integer targetSequenceOrder,
                                          @Param("tempOffset") int tempOffset,
                                          @Param("normalizeBy") int normalizeBy);

    default void shiftSequenceOrdersUp(Long candidateId, Integer targetSequenceOrder) {
        final int tempOffset = 1000;
        final int normalizeBy = tempOffset - 1;
        bumpSequenceOrdersForShift(candidateId, targetSequenceOrder, tempOffset);
        normalizeSequenceOrdersAfterShift(candidateId, targetSequenceOrder, tempOffset, normalizeBy);
    }

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("DELETE FROM CandidateStepPipeline cps WHERE cps.candidate.id = :candidateId AND cps.step.isVisible = false")
    int deleteInvisibleStepsByCandidateId(@Param("candidateId") Long candidateId);
}
