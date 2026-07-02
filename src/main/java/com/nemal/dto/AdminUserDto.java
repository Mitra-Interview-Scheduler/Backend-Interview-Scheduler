package com.nemal.dto;

import com.nemal.entity.User;
import com.nemal.enums.Role;

import java.util.Set;

public record AdminUserDto(
        Long id,
        String email,
        String firstName,
        String lastName,
        Set<Role> roles,
        boolean active,
        String designationName,
        String departmentName,
        Long departmentId,
        Long designationId,
        Long tierId,
        Integer yearsOfExperience
) {
    public static AdminUserDto from(User user) {
        return new AdminUserDto(
                user.getId(),
                user.getEmail(),
                user.getFirstName(),
                user.getLastName(),
                user.getRoles(),
                user.isActive(),
                user.getCurrentDesignation() != null ? user.getCurrentDesignation().getName() : null,
                user.getDepartment() != null ? user.getDepartment().getName() : null,
                user.getDepartment() != null ? user.getDepartment().getId() : null,
                user.getCurrentDesignation() != null ? user.getCurrentDesignation().getId() : null,
                user.getCurrentDesignation() != null && user.getCurrentDesignation().getTier() != null
                        ? user.getCurrentDesignation().getTier().getId()
                        : null,
                user.getYearsOfExperience() != null ? user.getYearsOfExperience() : 0
        );
    }
}