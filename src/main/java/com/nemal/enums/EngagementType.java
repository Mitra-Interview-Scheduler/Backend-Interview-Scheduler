package com.nemal.enums;

import lombok.Getter;

@Getter
public enum EngagementType {
    FULL_TIME("Full Time"),
    CONTRACT("Contract"),
    PART_TIME("Part Time");

    private final String label;

    EngagementType(String label) {
        this.label = label;
    }
}