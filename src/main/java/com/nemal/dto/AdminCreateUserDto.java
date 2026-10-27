package com.nemal.dto;

import com.nemal.enums.Role;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

import java.util.Set;

public record AdminCreateUserDto(
        @NotBlank @Email String email,
        @NotBlank @Size(min = 6) String password,
        @NotBlank String firstName,
        @NotBlank String lastName,
        @NotEmpty Set<Role> roles
) {
}
