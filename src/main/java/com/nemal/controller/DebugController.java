package com.nemal.controller;

import com.nemal.entity.User;
import com.nemal.repository.UserRepository;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/debug")
@PreAuthorize("hasRole('ADMIN')")
public class DebugController {

    private final UserRepository userRepository;

    public DebugController(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @GetMapping("/interviewer-techs/{interviewerId}")
    public Map<String, Object> debugInterviewerTechs(@PathVariable Long interviewerId) {
        Map<String, Object> debug = new HashMap<>();

        User user = userRepository.findById(interviewerId).orElse(null);
        if (user == null) {
            debug.put("error", "User not found");
            return debug;
        }

        debug.put("userId", user.getId());
        debug.put("email", user.getEmail());

        // Force load the collection
        var techs = user.getInterviewerTechnologies();
        debug.put("techCount", techs == null ? 0 : techs.size());

        if (techs != null && !techs.isEmpty()) {
            debug.put("technologies", techs.stream()
                    .map(it -> {
                        Map<String, Object> techInfo = new HashMap<>();
                        techInfo.put("id", it.getId());
                        techInfo.put("technologyId", it.getTechnology() != null ? it.getTechnology().getId() : null);
                        techInfo.put("technologyName", it.getTechnology() != null ? it.getTechnology().getName() : null);
                        techInfo.put("technologyActive", it.getTechnology() != null ? it.getTechnology().isActive() : null);
                        techInfo.put("assignmentActive", it.isActive());
                        techInfo.put("yearsExp", it.getYearsOfExperience());
                        return techInfo;
                    })
                    .collect(Collectors.toList())
            );
        }

        return debug;
    }
}