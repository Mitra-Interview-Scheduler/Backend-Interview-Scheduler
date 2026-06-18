package com.nemal.repository;

import com.nemal.entity.ClosingReason;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ClosingReasonRepository extends JpaRepository<ClosingReason, Long> {
    List<ClosingReason> findByIsActiveTrueOrderByDisplayOrderAsc();
}
