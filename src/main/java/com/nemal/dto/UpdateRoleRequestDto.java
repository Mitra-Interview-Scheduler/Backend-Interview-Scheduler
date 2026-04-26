package com.nemal.dto;

import com.nemal.enums.Role;
import jakarta.validation.constraints.NotNull;

public record UpdateRoleRequestDto(
        @NotNull Role role

) {
}
