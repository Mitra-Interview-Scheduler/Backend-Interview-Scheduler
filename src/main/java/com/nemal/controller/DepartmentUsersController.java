package com.nemal.controller;

import com.nemal.dto.DepartmentUserDto;
import com.nemal.entity.User;
import com.nemal.enums.Role;
import com.nemal.repository.DepartmentRepository;
import com.nemal.repository.UserRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Comparator;
import java.util.List;

@RestController
@RequestMapping("/api/hr/department-users")
@CrossOrigin(origins = "http://localhost:5173")
public class DepartmentUsersController {

    private final UserRepository userRepository;
    private final DepartmentRepository departmentRepository;

    public DepartmentUsersController(UserRepository userRepository, DepartmentRepository departmentRepository) {
        this.userRepository = userRepository;
        this.departmentRepository = departmentRepository;
    }

    /**
     * List active users for HR pickers.
     * - departmentId only → all active users in that department
     * - role only → all active users with that role (HR/ADMIN/INTERVIEWER)
     * - both → active users in department with that role
     * - minTierOrder → keep users whose designation tierOrder >= minTierOrder
     */
    @GetMapping
    public ResponseEntity<List<DepartmentUserDto>> getDepartmentUsers(
            @RequestParam(required = false) String role,
            @RequestParam(required = false) Long departmentId,
            @RequestParam(required = false) Integer minTierOrder
    ) {
        if (departmentId == null && (role == null || role.isBlank())) {
            throw new IllegalArgumentException("Either role or departmentId is required");
        }

        if (departmentId != null) {
            departmentRepository.findById(departmentId)
                    .orElseThrow(() -> new IllegalArgumentException("Department not found"));
        }

        List<User> users;
        if (departmentId != null && (role == null || role.isBlank())) {
            users = userRepository.findByDepartment_IdAndIsActiveTrueOrderByFirstNameAscLastNameAsc(departmentId);
        } else if (departmentId != null) {
            Role targetRole = parseRole(role);
            users = userRepository.findByDepartmentAndRole(departmentId, targetRole);
        } else {
            Role targetRole = parseRole(role);
            users = userRepository.findByRolesContaining(targetRole);
        }

        List<DepartmentUserDto> result = users.stream()
                .filter(User::isActive)
                .filter(u -> matchesMinTier(u, minTierOrder))
                .sorted(Comparator.comparing(User::getFullName, String.CASE_INSENSITIVE_ORDER))
                .map(DepartmentUserDto::from)
                .toList();

        return ResponseEntity.ok(result);
    }

    private boolean matchesMinTier(User user, Integer minTierOrder) {
        if (minTierOrder == null) {
            return true;
        }
        if (user.getCurrentDesignation() == null || user.getCurrentDesignation().getTier() == null) {
            return false;
        }
        Integer order = user.getCurrentDesignation().getTier().getTierOrder();
        return order != null && order >= minTierOrder;
    }

    private Role parseRole(String role) {
        try {
            Role targetRole = Role.valueOf(role.toUpperCase());
            if (targetRole != Role.HR && targetRole != Role.ADMIN && targetRole != Role.INTERVIEWER) {
                throw new IllegalArgumentException("Only HR, Admin, or Interviewer role filters are supported");
            }
            return targetRole;
        } catch (IllegalArgumentException ex) {
            if (ex.getMessage() != null && ex.getMessage().contains("Only HR")) {
                throw ex;
            }
            throw new IllegalArgumentException("Invalid role: " + role);
        }
    }
}
