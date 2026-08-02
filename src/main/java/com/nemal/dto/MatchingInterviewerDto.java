package com.nemal.dto;

import java.util.List;

public record MatchingInterviewerDto(
        Long interviewerId,
        String interviewerName,
        String email,
        String department,
        String designation,
        Integer yearsOfExperience,
        Integer interviewerTierOrder,
        Integer interviewerLevelOrder,
        List<String> matchedCore,
        List<String> matchedNonCore,
        List<String> matchedDomains,
        List<String> coreTechnologies,
        List<String> nonCoreTechnologies,
        List<String> domains,
        int coreMatchCount,
        int nonCoreMatchCount,
        int techMatchCount,
        int domainMatchCount,
        int totalMatches,
        boolean hasFreeTimeInWeek
) {}
