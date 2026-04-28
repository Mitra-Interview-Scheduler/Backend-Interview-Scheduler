package com.nemal.dto;

import com.nemal.entity.User;
import com.nemal.enums.Role;

public record UserDto(
        Long id,
        String email,
        String firstName,
        String lastName,
        Role role,
        String designationName
) {

    public static UserDto from(User user) {
        return new UserDto(user.getId(), user.getEmail(), user.getFirstName(), user.getLastName(), user.getRole(), user.getCurrentDesignation() != null ? user.getCurrentDesignation().getName() : null);
    }
}