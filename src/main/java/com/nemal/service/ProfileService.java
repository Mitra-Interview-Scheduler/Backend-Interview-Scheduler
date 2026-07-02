package com.nemal.service;

import com.nemal.dto.*;
import com.nemal.entity.*;
import com.nemal.enums.Role;
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

        if (isAdmin(user)) {
            applyProfessionalDetails(existingUser, updateDto.departmentId(), updateDto.designationId(), updateDto.yearsOfExperience());
        } else if (hasProfessionalDetailChanges(updateDto)) {
            throw new IllegalArgumentException("Professional details can only be updated by an administrator");
        }

        existingUser = userRepository.save(existingUser);
        return ProfileDto.from(existingUser);
    }

    @Transactional
    public AdminUserDto updateProfessionalDetails(Long userId, AdminProfessionalDetailsUpdateDto updateDto) {
        User existingUser = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found"));

        existingUser.setYearsOfExperience(
                updateDto.yearsOfExperience() != null ? updateDto.yearsOfExperience() : 0
        );

        if (updateDto.departmentId() == null) {
            existingUser.setDepartment(null);
            existingUser.setCurrentDesignation(null);
        } else {
            Department department = departmentRepository.findById(updateDto.departmentId())
                    .orElseThrow(() -> new RuntimeException("Department not found"));
            existingUser.setDepartment(department);

            if (updateDto.designationId() == null) {
                existingUser.setCurrentDesignation(null);
            } else {
                Designation designation = designationRepository.findById(updateDto.designationId())
                        .orElseThrow(() -> new RuntimeException("Designation not found"));

                if (!designation.getDepartment().getId().equals(department.getId())) {
                    throw new RuntimeException("Designation must belong to the user's department");
                }

                existingUser.setCurrentDesignation(designation);
            }
        }

        existingUser = userRepository.save(existingUser);
        return AdminUserDto.from(existingUser);
    }

    @Transactional
    public AdminUserDto updateBasicInfo(Long userId, AdminUserBasicInfoUpdateDto updateDto) {
        User existingUser = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found"));

        if (updateDto.firstName() == null || updateDto.firstName().isBlank()) {
            throw new IllegalArgumentException("First name is required");
        }
        if (updateDto.lastName() == null || updateDto.lastName().isBlank()) {
            throw new IllegalArgumentException("Last name is required");
        }

        existingUser.setFirstName(updateDto.firstName().trim());
        existingUser.setLastName(updateDto.lastName().trim());

        existingUser = userRepository.save(existingUser);
        return AdminUserDto.from(existingUser);
    }

    private boolean isAdmin(User user) {
        return user.getRoles().contains(Role.ADMIN);
    }

    private boolean hasProfessionalDetailChanges(ProfileUpdateDto updateDto) {
        return updateDto.departmentId() != null
                || updateDto.designationId() != null
                || updateDto.yearsOfExperience() != null;
    }

    private void applyProfessionalDetails(
            User existingUser,
            Long departmentId,
            Long designationId,
            Integer yearsOfExperience
    ) {
        if (yearsOfExperience != null) {
            existingUser.setYearsOfExperience(yearsOfExperience);
        }

        if (departmentId != null) {
            Department department = departmentRepository.findById(departmentId)
                    .orElseThrow(() -> new RuntimeException("Department not found"));
            existingUser.setDepartment(department);
        }

        if (designationId != null) {
            Designation designation = designationRepository.findById(designationId)
                    .orElseThrow(() -> new RuntimeException("Designation not found"));

            if (existingUser.getDepartment() != null &&
                    !designation.getDepartment().getId().equals(existingUser.getDepartment().getId())) {
                throw new RuntimeException("Designation must belong to the user's department");
            }

            existingUser.setCurrentDesignation(designation);
        }
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