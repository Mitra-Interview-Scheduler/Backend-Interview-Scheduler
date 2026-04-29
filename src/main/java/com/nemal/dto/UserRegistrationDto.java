package com.nemal.dto;

import com.nemal.enums.Role;
import jakarta.validation.constraints.NotNull;

import java.util.Set;

public record UserRegistrationDto(
        @NotNull
        String email,
        String password,
        String firstName,
        String lastName,
        Set<Role> roles) {}