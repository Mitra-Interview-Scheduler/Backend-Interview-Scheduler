package com.nemal.dto;

import com.nemal.enums.Role;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.util.Set;

public record UpdateRoleRequestDto(
        @NotNull @NotEmpty Set<Role> roles
) {
}
