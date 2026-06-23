package com.nemal.dto;

import com.nemal.entity.User;

public record DepartmentUserDto(
        Long id,
        String fullName,
        String email
) {
    public static DepartmentUserDto from(User user) {
        return new DepartmentUserDto(
                user.getId(),
                user.getFullName().trim(),
                user.getEmail()
        );
    }
}
