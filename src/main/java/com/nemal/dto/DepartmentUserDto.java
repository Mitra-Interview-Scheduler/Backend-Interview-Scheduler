package com.nemal.dto;

import com.nemal.entity.User;

public record DepartmentUserDto(
        Long id,
        String fullName,
        String email,
        String designationName,
        String departmentName,
        Integer tierOrder,
        String tierName
) {
    public static DepartmentUserDto from(User user) {
        var designation = user.getCurrentDesignation();
        var department = user.getDepartment();
        var tier = designation != null ? designation.getTier() : null;
        return new DepartmentUserDto(
                user.getId(),
                user.getFullName().trim(),
                user.getEmail(),
                designation != null ? designation.getName() : null,
                department != null ? department.getName() : null,
                tier != null ? tier.getTierOrder() : null,
                tier != null ? tier.getName() : null
        );
    }
}
