package com.nemal.repository;

import com.nemal.entity.InterviewType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface InterviewTypeRepository extends JpaRepository<InterviewType, Long> {

    @Query("SELECT t FROM InterviewType t WHERE LOWER(t.code) = LOWER(:code)")
    Optional<InterviewType> findByCodeIgnoreCase(@Param("code") String code);

    boolean existsByCodeIgnoreCase(String code);

    List<InterviewType> findByActiveTrueOrderByDisplayOrderAscLabelAsc();

    List<InterviewType> findAllByOrderByDisplayOrderAscLabelAsc();

    @Query("select coalesce(max(t.displayOrder), 0) from InterviewType t")
    int findMaxDisplayOrder();
}
