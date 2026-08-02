package com.nemal.repository;

import com.nemal.entity.InterviewerTechnology;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;

@Repository
public interface InterviewerTechnologyRepository extends JpaRepository<InterviewerTechnology, Long> {

    @Query("""
            SELECT it FROM InterviewerTechnology it
            JOIN FETCH it.technology t
            LEFT JOIN FETCH t.category
            WHERE it.interviewer.id = :interviewerId
            """)
    List<InterviewerTechnology> findByInterviewerId(@Param("interviewerId") Long interviewerId);

    List<InterviewerTechnology> findByTechnologyId(Long technologyId);

    List<InterviewerTechnology> findByInterviewerIdAndIsActiveTrue(Long interviewerId);

    boolean existsByInterviewerIdAndTechnologyId(Long interviewerId, Long technologyId);

    @Query("""
            SELECT DISTINCT it.interviewer.id FROM InterviewerTechnology it
            WHERE it.isActive = true
            AND it.technology.id IN :technologyIds
            """)
    List<Long> findInterviewerIdsByTechnologyIds(@Param("technologyIds") Collection<Long> technologyIds);

    @Query("""
            SELECT it FROM InterviewerTechnology it
            JOIN FETCH it.technology t
            LEFT JOIN FETCH t.category
            WHERE it.interviewer.id IN :interviewerIds
            AND it.isActive = true
            """)
    List<InterviewerTechnology> findActiveByInterviewerIdIn(@Param("interviewerIds") Collection<Long> interviewerIds);
}