package com.nemal.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record LoginDto(
        @NotBlank(message = "Email cannot be blank")
        @NotNull(message = "Email is required")
        String email, String password) {}