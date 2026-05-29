package com.nemal.repository;

import com.nemal.entity.FeedbackForm;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface FeedbackFormRepository extends JpaRepository<FeedbackForm, Long> {
    Optional<FeedbackForm> findFirstByIsActiveTrueOrderByIdDesc();
    java.util.List<FeedbackForm> findAllByIsActiveTrue();
    java.util.List<FeedbackForm> findBySeriesKey(String seriesKey);
}
