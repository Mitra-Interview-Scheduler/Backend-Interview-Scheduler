package com.nemal.enums;

import java.util.Arrays;

public enum PipelineAuditActionType {
    APPLICATION_CREATED("NWC"),
    STATUS_CHANGED("STC"),
    SCREENING_SAVED("SCS"),
    APPLICATION_CLOSED("ACL"),
    INTERVIEW_SCHEDULED("IVS"),
    INTERVIEW_CANCELLED("IVC"),
    FEEDBACK_SUBMITTED("FBS");

    private final String code;

    PipelineAuditActionType(String code) {
        this.code = code;
    }

    public String getCode() {
        return code;
    }

    public static PipelineAuditActionType fromCode(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String normalized = value.trim();
        return Arrays.stream(values())
                .filter(action -> action.code.equalsIgnoreCase(normalized)
                        || action.name().equalsIgnoreCase(normalized))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unknown pipeline audit action type: " + value));
    }
}
