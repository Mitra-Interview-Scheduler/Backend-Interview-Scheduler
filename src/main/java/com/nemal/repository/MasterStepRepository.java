package com.nemal.repository;

import com.nemal.entity.MasterStep;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface MasterStepRepository extends JpaRepository<MasterStep, Long> {
    List<MasterStep> findByIsActiveTrueOrderByStepOrderAscDisplayOrderAsc();

    List<MasterStep>findAllByIsActiveTrueAndIsVisibleTrueOrderByStepOrderAsc();

    MasterStep findByStatusKey(String statusKey);

    List<MasterStep> findByIsDefaultStepTrueOrderByStepOrderAsc();

    List<MasterStep> findAllByIsActiveTrueAndIsClosingStepTrueOrderByDisplayOrderAsc();
}
