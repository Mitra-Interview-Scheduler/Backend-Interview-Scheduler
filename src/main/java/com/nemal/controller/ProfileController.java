package com.nemal.controller;

import com.nemal.dto.*;
import com.nemal.entity.User;
import com.nemal.service.DepartmentService;
import com.nemal.service.ProfileService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api")
@CrossOrigin(origins = "http://localhost:5173")
public class ProfileController {

    private final ProfileService profileService;
    private final DepartmentService departmentService;

    public ProfileController(ProfileService profileService, DepartmentService departmentService) {
        this.profileService = profileService;
        this.departmentService = departmentService;
    }

    @GetMapping("/profile")
    public ResponseEntity<ProfileDto> getProfile(@AuthenticationPrincipal User user) {
        return ResponseEntity.ok(profileService.getProfile(user));
    }

    @PutMapping("/profile")
    public ResponseEntity<ProfileDto> updateProfile(
            @AuthenticationPrincipal User user,
            @RequestBody ProfileUpdateDto updateDto
    ) {
        return ResponseEntity.ok(profileService.updateProfile(user, updateDto));
    }

    // REMOVED: Technology endpoints - now in TechnologyController
    // @GetMapping("/technologies")
    // @PostMapping("/technologies")

    @GetMapping("/profile/interviewer-technologies")
    public ResponseEntity<List<InterviewerTechnologyDto>> getInterviewerTechnologies(
            @AuthenticationPrincipal User user
    ) {
        return ResponseEntity.ok(profileService.getInterviewerTechnologies(user.getId()));
    }

    @PostMapping("/profile/interviewer-technologies")
    public ResponseEntity<InterviewerTechnologyDto> addInterviewerTechnology(
            @AuthenticationPrincipal User user,
            @RequestBody AddInterviewerTechnologyDto dto
    ) {
        return ResponseEntity.ok(profileService.addInterviewerTechnology(user, dto));
    }

    @PutMapping("/profile/interviewer-technologies/{id}")
    public ResponseEntity<InterviewerTechnologyDto> updateInterviewerTechnology(
            @AuthenticationPrincipal User user,
            @PathVariable Long id,
            @RequestBody UpdateInterviewerTechnologyDto dto
    ) {
        return ResponseEntity.ok(profileService.updateInterviewerTechnology(user.getId(), id, dto));
    }

    @DeleteMapping("/profile/interviewer-technologies/{id}")
    public ResponseEntity<Void> removeInterviewerTechnology(
            @AuthenticationPrincipal User user,
            @PathVariable Long id
    ) {
        profileService.removeInterviewerTechnology(user.getId(), id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/departments")
    public ResponseEntity<List<DepartmentDto>> getAllDepartments() {
        return ResponseEntity.ok(departmentService.getAllDepartments());
    }

    @PostMapping("/departments")
    public ResponseEntity<DepartmentDto> createDepartment(@RequestBody CreateDepartmentDto dto) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(departmentService.createDepartment(dto));
    }

    // Note: Designations are now in DesignationController at /api/designations
}