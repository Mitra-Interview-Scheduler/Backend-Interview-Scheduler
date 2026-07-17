package com.nemal.dto;

import com.nemal.entity.User;
import com.nemal.enums.Role;

import java.util.Set;

public record LoginResponse(
        String token,
        Long id,
        String email,
        String firstName,
        String lastName,
        Set<Role> roles,
        String profilePictureUrl
) {
    public static LoginResponse from(String token, User user) {
        return new LoginResponse(
                token,
                user.getId(),
                user.getEmail(),
                user.getFirstName(),
                user.getLastName(),
                user.getRoles(),
                user.getProfilePictureUrl()
        );
    }
}