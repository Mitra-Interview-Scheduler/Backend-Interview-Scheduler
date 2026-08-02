package com.nemal.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonProperty;

public record UpdateInterviewTypeDto(
        String label,
        String description,
        Boolean active,
        Integer displayOrder,
        String roundStatusKey,
        String cancelRestoreStatusKey,
        @JsonProperty("createCalendarMeeting")
        @JsonAlias({"create_calendar_meeting", "createMeeting", "meetingEnabled"})
        Boolean createCalendarMeeting,
        @JsonProperty("requiresInterviewer")
        @JsonAlias({"requires_interviewer"})
        Boolean requiresInterviewer,
        InterviewTypeFilterRulesDto filterRules
) {}
