package com.nemal.controller;

import com.nemal.dto.AdminUserDto;
import com.nemal.dto.PaginatedResponseDto;
import com.nemal.dto.UpdateRoleRequestDto;
import com.nemal.entity.User;
import com.nemal.repository.UserRepository;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.data.domain.Sort;

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
    public ResponseEntity<?> getAllUsers(
            @RequestParam(required = false) String search,
            @RequestParam(required = false) Role role,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size
    ) {
        boolean hasFilters = (search != null && !search.isBlank()) || role != null || (status != null && !status.isBlank());
        if (page != null || size != null || hasFilters) {
            int pageValue = page != null ? Math.max(0, page) : 0;
            int sizeValue = size != null ? Math.max(1, size) : 10;
            String q = search != null ? search.trim().toLowerCase() : null;
            final Boolean activeFilter = (status != null && !status.isBlank() && !status.equalsIgnoreCase("ALL"))
                    ? (status.equalsIgnoreCase("ACTIVE") ? Boolean.TRUE
                    : status.equalsIgnoreCase("INACTIVE") ? Boolean.FALSE : null)
                    : null;

            List<User> filteredUsers = userRepository.findAll(Sort.by(Sort.Direction.DESC, "createdAt"))
                    .stream()
                    .filter(u -> {
                        if (role != null && !u.getRoles().contains(role)) return false;
                        if (activeFilter != null && u.isActive() != activeFilter) return false;
                        if (q == null || q.isBlank()) return true;
                        String fullName = ((u.getFirstName() != null ? u.getFirstName() : "") + " " +
                                (u.getLastName() != null ? u.getLastName() : "")).toLowerCase();
                        String email = u.getEmail() != null ? u.getEmail().toLowerCase() : "";
                        return fullName.contains(q) || email.contains(q);
                    })
                    .toList();

            int total = filteredUsers.size();
            int fromIndex = Math.min(pageValue * sizeValue, total);
            int toIndex = Math.min(fromIndex + sizeValue, total);
            int totalPages = Math.max(1, (int) Math.ceil((double) total / sizeValue));

            List<AdminUserDto> content = filteredUsers.subList(fromIndex, toIndex)
                    .stream().map(AdminUserDto::from).toList();

            return ResponseEntity.ok(new PaginatedResponseDto<>(
                    content,
                    pageValue,
                    sizeValue,
                    total,
                    totalPages,
                    pageValue == 0,
                    pageValue >= totalPages - 1
            ));
        }

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