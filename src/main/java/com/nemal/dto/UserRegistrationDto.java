package com.nemal.dto;

import jakarta.validation.constraints.NotNull;

public record UserRegistrationDto(
        @NotNull
        String email,
        String password,
        String firstName,
        String lastName) {}
