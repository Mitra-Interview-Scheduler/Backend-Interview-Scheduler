package com.nemal.dto;

import java.util.List;

public record InterviewerMatchResponseDto(
        List<MatchingInterviewerDto> both,
        List<MatchingInterviewerDto> technologies,
        List<MatchingInterviewerDto> domains
) {
    public static InterviewerMatchResponseDto empty() {
        return new InterviewerMatchResponseDto(List.of(), List.of(), List.of());
    }
}
