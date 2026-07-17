package com.nemal.enums;

public enum InterviewType {
    TECHNICAL,
    HR;

    public MasterStatus toCandidateStatus() {
        return this == HR ? MasterStatus.HR_ROUND : MasterStatus.TECHNICAL_ROUND;
    }

    /** Macro status to restore when this interview type is cancelled and no other interviews are active. */
    public MasterStatus statusAfterInterviewCancel() {
        return this == HR ? MasterStatus.TECHNICAL_ROUND : MasterStatus.SCREENING;
    }

    public static InterviewType fromValue(String value) {
        if (value == null || value.isBlank()) {
            return TECHNICAL;
        }
        return InterviewType.valueOf(value.trim().toUpperCase());
    }
}
