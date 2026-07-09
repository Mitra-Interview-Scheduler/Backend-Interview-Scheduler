package com.nemal.dto;

import com.nemal.entity.User;
import com.nemal.util.RoleUtils;
import com.nemal.enums.Role;

import java.util.Set;

public record UserDto(
        Long id,
        String email,
        String firstName,
        String lastName,
        Set<Role> roles,
        String designationName
) {

    public static UserDto from(User user) {
        return new UserDto(user.getId(), user.getEmail(), user.getFirstName(), user.getLastName(), RoleUtils.sortRoles(user.getRoles()), user.getCurrentDesignation() != null ? user.getCurrentDesignation().getName() : null);
    }
}