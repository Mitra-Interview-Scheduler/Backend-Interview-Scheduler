package com.nemal.repository;

import com.nemal.entity.FeedbackQuestion;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface FeedbackQuestionRepository extends JpaRepository<FeedbackQuestion, Long> {
    List<FeedbackQuestion> findByCategoryEqualsIgnoreCaseAndIsActiveTrueOrderByDisplayOrderAsc(String category);
    List<FeedbackQuestion> findByFormIdAndIsActiveTrueOrderByDisplayOrderAsc(Long formId);

    void deleteByFormId(Long formId);
}
