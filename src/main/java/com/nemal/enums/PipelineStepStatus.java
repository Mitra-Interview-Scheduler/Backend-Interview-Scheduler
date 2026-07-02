package com.nemal.enums;

import java.util.Arrays;

public enum PipelineStepStatus {
    PENDING("PND"),
    CURRENT("CUR"),
    COMPLETED("CMP"),
    FAILED("FAL"),
    SKIPPED("SKP");

    private final String code;

    PipelineStepStatus(String code) {
        this.code = code;
    }

    public String getCode() {
        return code;
    }

    public static PipelineStepStatus fromCode(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String normalized = value.trim();
        return Arrays.stream(values())
                .filter(status -> status.code.equalsIgnoreCase(normalized)
                        || status.name().equalsIgnoreCase(normalized))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unknown pipeline step status: " + value));
    }
}
