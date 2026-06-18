package com.nemal.service;

import com.nemal.dto.ClosingReasonDto;
import com.nemal.repository.ClosingReasonRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class ClosingReasonService {
    private final ClosingReasonRepository closingReasonRepository;

    public ClosingReasonService(ClosingReasonRepository closingReasonRepository) {
        this.closingReasonRepository = closingReasonRepository;
    }

    @Transactional(readOnly = true)
    public List<ClosingReasonDto> getActiveReasons() {
        return closingReasonRepository.findByIsActiveTrueOrderByDisplayOrderAsc()
                .stream()
                .map(ClosingReasonDto::from)
                .toList();
    }
}
