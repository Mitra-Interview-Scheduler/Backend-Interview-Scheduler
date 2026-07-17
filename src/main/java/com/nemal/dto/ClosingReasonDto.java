package com.nemal.dto;

import com.nemal.entity.ClosingReason;

public record ClosingReasonDto(
        Long id,
        String code,
        String label,
        Integer displayOrder
) {
    public static ClosingReasonDto from(ClosingReason reason) {
        return new ClosingReasonDto(
                reason.getId(),
                reason.getCode(),
                reason.getLabel(),
                reason.getDisplayOrder()
        );
    }
}
