package com.nemal.enums;

/**
 * How an interview type resolves an interviewer matching dimension.
 */
public enum InterviewerFilterMode {
    /** Use the candidate's value for this dimension. */
    SAME_AS_CANDIDATE,
    /** Use the admin-configured fixed value(s). */
    FIXED,
    /** Do not apply this filter. */
    NONE
}
