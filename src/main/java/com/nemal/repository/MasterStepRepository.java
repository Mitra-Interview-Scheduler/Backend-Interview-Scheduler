package com.nemal.repository;

import com.nemal.entity.MasterStep;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface MasterStepRepository extends JpaRepository<MasterStep, Long> {
    List<MasterStep> findByIsActiveTrueOrderByStepOrderAscDisplayOrderAsc();

    List<MasterStep>findAllByIsActiveTrueAndIsVisibleTrueOrderByStepOrderAsc();

    MasterStep findByStatusKey(String statusKey);

    boolean existsByStatusKeyIgnoreCase(String statusKey);

    List<MasterStep> findByIsDefaultStepTrueAndIsVisibleTrueOrderByStepOrderAscDisplayOrderAsc();

    List<MasterStep> findByIsDefaultStepTrueAndIsActiveTrueOrderByStepOrderAscDisplayOrderAsc();

    List<MasterStep> findByIsDefaultStepTrueOrderByStepOrderAscDisplayOrderAsc();

    List<MasterStep> findAllByIsActiveTrueAndIsClosingStepTrueAndIsVisibleTrueOrderByDisplayOrderAsc();

    @Query("select coalesce(max(s.displayOrder), 0) from MasterStep s")
    int findMaxDisplayOrder();
}
