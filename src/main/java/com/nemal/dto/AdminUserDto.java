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
        String departmentName
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
                user.getDepartment() != null ? user.getDepartment().getName() : null
        );
    }
}