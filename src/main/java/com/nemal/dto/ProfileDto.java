package com.nemal.dto;

import com.nemal.entity.User;
import com.nemal.util.RoleUtils;

import java.util.List;

public record ProfileDto(
        Long id,
        String email,
        String firstName,
        String lastName,
        String phone,
        String profilePictureUrl,
        List<String> roles,
        DepartmentSimpleDto department,
        DesignationSimpleDto currentDesignation,
        Integer yearsOfExperience,
        String bio,
        UserSettingsDto settings
) {
    public static ProfileDto from(User user) {
        return new ProfileDto(
                user.getId(),
                user.getEmail(),
                user.getFirstName(),
                user.getLastName(),
                user.getPhone(),
                user.getProfilePictureUrl(),
                RoleUtils.toSortedRoleNames(user.getRoles()),
                user.getDepartment() != null ? DepartmentSimpleDto.from(user.getDepartment()) : null,
                user.getCurrentDesignation() != null ? DesignationSimpleDto.from(user.getCurrentDesignation()) : null,
                user.getYearsOfExperience() != null ? user.getYearsOfExperience() : 0,
                user.getBio(),
                UserSettingsDto.from(user.getSettings())
        );
    }
}