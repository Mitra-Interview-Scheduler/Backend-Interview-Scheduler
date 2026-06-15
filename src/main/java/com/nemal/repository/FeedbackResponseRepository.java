package com.nemal.repository;

import com.nemal.entity.FeedbackResponse;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface FeedbackResponseRepository extends JpaRepository<FeedbackResponse, Long> {
    Optional<FeedbackResponse> findByInterviewScheduleId(Long interviewScheduleId);
    Optional<FeedbackResponse> findByInterviewScheduleIdAndInterviewerId(Long interviewScheduleId, Long interviewerId);
    boolean existsByInterviewScheduleId(Long interviewScheduleId);
    boolean existsByFormId(Long formId);
}
