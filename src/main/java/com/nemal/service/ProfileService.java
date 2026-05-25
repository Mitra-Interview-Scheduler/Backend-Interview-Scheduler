package com.nemal.service;

import com.nemal.dto.*;
import com.nemal.entity.*;
import com.nemal.repository.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
public class ProfileService {

    private final UserRepository userRepository;
    private final TechnologyRepository technologyRepository;
    private final InterviewerTechnologyRepository interviewerTechnologyRepository;
    private final DepartmentRepository departmentRepository;
    private final DesignationRepository designationRepository;

    public ProfileService(
            UserRepository userRepository,
            TechnologyRepository technologyRepository,
            InterviewerTechnologyRepository interviewerTechnologyRepository,
            DepartmentRepository departmentRepository,
            DesignationRepository designationRepository
    ) {
        this.userRepository = userRepository;
        this.technologyRepository = technologyRepository;
        this.interviewerTechnologyRepository = interviewerTechnologyRepository;
        this.departmentRepository = departmentRepository;
        this.designationRepository = designationRepository;
    }

    @Transactional(readOnly = true)
    public ProfileDto getProfile(User user) {
        User freshUser = userRepository.findById(user.getId())
                .orElseThrow(() -> new RuntimeException("User not found"));
        return ProfileDto.from(freshUser);
    }

    @Transactional
    public ProfileDto updateProfile(User user, ProfileUpdateDto updateDto) {
        User existingUser = userRepository.findById(user.getId())
                .orElseThrow(() -> new RuntimeException("User not found"));

        if (updateDto.firstName() != null) {
            existingUser.setFirstName(updateDto.firstName());
        }
        if (updateDto.lastName() != null) {
            existingUser.setLastName(updateDto.lastName());
        }
        if (updateDto.phone() != null) {
            existingUser.setPhone(updateDto.phone());
        }
        if (updateDto.profilePictureUrl() != null) {
            existingUser.setProfilePictureUrl(updateDto.profilePictureUrl());
        }
        if (updateDto.bio() != null) {
            existingUser.setBio(updateDto.bio());
        }
        if (updateDto.yearsOfExperience() != null) {
            existingUser.setYearsOfExperience(updateDto.yearsOfExperience());
        }

        // Update department
        if (updateDto.departmentId() != null) {
            Department department = departmentRepository.findById(updateDto.departmentId())
                    .orElseThrow(() -> new RuntimeException("Department not found"));
            existingUser.setDepartment(department);
        }

        // Update designation (which includes tier information)
        if (updateDto.designationId() != null) {
            Designation designation = designationRepository.findById(updateDto.designationId())
                    .orElseThrow(() -> new RuntimeException("Designation not found"));

            // Validate that designation belongs to the user's department
            if (existingUser.getDepartment() != null &&
                    !designation.getDepartment().getId().equals(existingUser.getDepartment().getId())) {
                throw new RuntimeException("Designation must belong to your department");
            }

            existingUser.setCurrentDesignation(designation);
        }

        existingUser = userRepository.save(existingUser);
        return ProfileDto.from(existingUser);
    }

    public List<InterviewerTechnologyDto> getInterviewerTechnologies(Long userId) {
        return interviewerTechnologyRepository.findByInterviewerId(userId).stream()
                .map(InterviewerTechnologyDto::from)
                .collect(Collectors.toList());
    }

    @Transactional
    public InterviewerTechnologyDto addInterviewerTechnology(User user, AddInterviewerTechnologyDto dto) {
        Technology technology = technologyRepository.findById(dto.technologyId())
                .orElseThrow(() -> new RuntimeException("Technology not found"));

        // Check if already exists
        boolean exists = interviewerTechnologyRepository.existsByInterviewerIdAndTechnologyId(
                user.getId(),
                dto.technologyId()
        );

        if (exists) {
            throw new RuntimeException("This technology is already added to your profile");
        }

        InterviewerTechnology it = InterviewerTechnology.builder()
                .interviewer(user)
                .technology(technology)
                .yearsOfExperience(dto.yearsOfExperience())
                .isActive(true)
                .build();

        it = interviewerTechnologyRepository.save(it);
        return InterviewerTechnologyDto.from(it);
    }

    @Transactional
    public void removeInterviewerTechnology(Long userId, Long interviewerTechId) {
        InterviewerTechnology it = interviewerTechnologyRepository.findById(interviewerTechId)
                .orElseThrow(() -> new RuntimeException("Technology assignment not found"));

        if (!it.getInterviewer().getId().equals(userId)) {
            throw new RuntimeException("Unauthorized");
        }

        interviewerTechnologyRepository.delete(it);
    }

    public List<DepartmentDto> getAllDepartments() {
        return departmentRepository.findAll().stream()
                .map(DepartmentDto::from)
                .collect(Collectors.toList());
    }
}