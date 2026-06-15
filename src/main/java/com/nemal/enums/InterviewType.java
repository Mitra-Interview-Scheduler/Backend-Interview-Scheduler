package com.nemal.enums;

public enum InterviewType {
    TECHNICAL,
    HR;

    public MasterStatus toCandidateStatus() {
        return this == HR ? MasterStatus.HR_ROUND : MasterStatus.TECHNICAL_ROUND;
    }

    public static InterviewType fromValue(String value) {
        if (value == null || value.isBlank()) {
            return TECHNICAL;
        }
        return InterviewType.valueOf(value.trim().toUpperCase());
    }
}
