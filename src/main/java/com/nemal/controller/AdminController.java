package com.nemal.controller;

import com.nemal.dto.AdminUserDto;
import com.nemal.dto.UpdateRoleRequestDto;
import com.nemal.entity.User;
import com.nemal.repository.UserRepository;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import com.nemal.enums.Role;

@RestController
@RequestMapping("/api/admin")
@CrossOrigin(origins = "http://localhost:5173")
@PreAuthorize("hasRole('ADMIN')")
public class AdminController {

    private final UserRepository userRepository;

    public AdminController(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    // GET /api/admin/users
    @GetMapping("/users")
    public ResponseEntity<List<AdminUserDto>> getAllUsers() {
        List<AdminUserDto> users = userRepository.findAll()
                .stream()
                .map(AdminUserDto::from)
                .toList();
        return ResponseEntity.ok(users);
    }

    // PATCH /api/admin/users/{id}/status  — toggles isActive
    @PatchMapping("/users/{id}/status")
    public ResponseEntity<AdminUserDto> toggleUserStatus(@PathVariable Long id) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("User not found: " + id));
        user.setActive(!user.isActive());
        userRepository.save(user);
        return ResponseEntity.ok(AdminUserDto.from(user));
    }

    // PUT /api/admin/users/{id}/roles  — replace all roles
    @PutMapping("/users/{id}/roles")
    public ResponseEntity<AdminUserDto> setUserRoles(@PathVariable Long id, @Valid @RequestBody UpdateRoleRequestDto request) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("User not found: " + id));

        // Prevent removing all ADMIN roles from the system
        if (request.roles().isEmpty() || !request.roles().contains(Role.ADMIN)) {
            long adminCount = userRepository.findAll().stream()
                    .filter(u -> u.getRoles().contains(Role.ADMIN) && !u.getId().equals(id))
                    .count();
            if (adminCount == 0) {
                throw new RuntimeException("Cannot remove ADMIN role: at least one admin user required");
            }
        }

        user.setRoles(new HashSet<>(request.roles()));
        userRepository.save(user);
        return ResponseEntity.ok(AdminUserDto.from(user));
    }

    // POST /api/admin/users/{id}/roles/{role}  — add a single role
    @PostMapping("/users/{id}/roles/{role}")
    public ResponseEntity<AdminUserDto> addRoleToUser(@PathVariable Long id, @PathVariable String role) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("User not found: " + id));

        try {
            Role roleEnum = Role.valueOf(role.toUpperCase());
            Set<Role> roles = new HashSet<>(user.getRoles());
            roles.add(roleEnum);
            user.setRoles(roles);
            userRepository.save(user);
            return ResponseEntity.ok(AdminUserDto.from(user));
        } catch (IllegalArgumentException ex) {
            throw new RuntimeException("Invalid role: " + role);
        }
    }

    // DELETE /api/admin/users/{id}/roles/{role}  — remove a single role
    @DeleteMapping("/users/{id}/roles/{role}")
    public ResponseEntity<AdminUserDto> removeRoleFromUser(@PathVariable Long id, @PathVariable String role) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("User not found: " + id));

        try {
            Role roleEnum = Role.valueOf(role.toUpperCase());

            // Prevent removing the last ADMIN role from the system
            if (roleEnum == Role.ADMIN && user.getRoles().contains(Role.ADMIN)) {
                long adminCount = userRepository.findAll().stream()
                        .filter(u -> u.getRoles().contains(Role.ADMIN) && !u.getId().equals(id))
                        .count();
                if (adminCount == 0) {
                    throw new RuntimeException("Cannot remove ADMIN role: at least one admin user required");
                }
            }

            Set<Role> roles = new HashSet<>(user.getRoles());
            roles.remove(roleEnum);

            if (roles.isEmpty()) {
                throw new RuntimeException("User must have at least one role");
            }

            user.setRoles(roles);
            userRepository.save(user);
            return ResponseEntity.ok(AdminUserDto.from(user));
        } catch (IllegalArgumentException ex) {
            throw new RuntimeException("Invalid role: " + role);
        }
    }

    // DELETE /api/admin/users/{id}
    @DeleteMapping("/users/{id}")
    public ResponseEntity<Void> deleteUser(@PathVariable Long id) {
        if (!userRepository.existsById(id)) {
            return ResponseEntity.notFound().build();
        }
        userRepository.deleteById(id);
        return ResponseEntity.noContent().build();
    }
}