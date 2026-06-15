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
public interface CandidateStepPipelineRepository extends JpaRepository <CandidateStepPipeline, Long>{


    List<CandidateStepPipeline> findByCandidateIdOrderBySequenceOrderAsc(Long candidateId);


    @Modifying
    @Query("UPDATE CandidateStepPipeline cps SET cps.stepStatus = :status " +
            "WHERE cps.candidate.id = :candidateId AND cps.sequenceOrder = :sequenceOrder")
    int updateStepStatus(@Param("candidateId") Long candidateId,
                         @Param("sequenceOrder") Integer sequenceOrder,
                         @Param("status") PipelineStepStatus status);


    // Automatically flips all downstream PENDING steps to SKIPPED on failure
    @Modifying
    @Query("UPDATE CandidateStepPipeline cps SET cps.stepStatus = 'SKIPPED' " +
            "WHERE cps.candidate.id = :candidateId " +
            "AND cps.sequenceOrder > :currentSequenceOrder " +
            "AND cps.stepStatus = 'PENDING'")
    int skipUpcomingSteps(@Param("candidateId") Long candidateId,
                          @Param("currentSequenceOrder") Integer currentSequenceOrder);



    /**
     * Shifts all steps at or after the target sequence up by 1 to clear space for a new insertion.
     */
    @Modifying
    @Query("UPDATE CandidateStepPipeline cps SET cps.sequenceOrder = cps.sequenceOrder + 1 " +
            "WHERE cps.candidate.id = :candidateId AND cps.sequenceOrder >= :targetSequenceOrder")
    int shiftSequenceOrdersUp(@Param("candidateId") Long candidateId,
                              @Param("targetSequenceOrder") Integer targetSequenceOrder);

}
