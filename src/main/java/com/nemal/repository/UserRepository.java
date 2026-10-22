package com.nemal.repository;

import com.nemal.entity.User;
import com.nemal.enums.Role;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {
    Optional<User> findByEmail(String email);

    List<User> findByRolesContaining(Role role);

    @Query("SELECT u FROM User u WHERE u.department.id = :deptId AND :role MEMBER OF u.roles")
    List<User> findByDepartmentAndRole(Long deptId, Role role);

    List<User> findByDepartment_IdAndIsActiveTrueOrderByFirstNameAscLastNameAsc(Long departmentId);

    @Query("SELECT u FROM User u WHERE u.currentDesignation.id IN :eligibleDesignationIds AND :role MEMBER OF u.roles")
    List<User> findByCurrentDesignationIdInAndRole(List<Long> eligibleDesignationIds, Role role);

    @Query("SELECT u FROM User u LEFT JOIN FETCH u.department WHERE SIZE(u.roles) = 0")
    List<User> findUsersWithNoRoles();

    @Query("""
            SELECT u FROM User u
            WHERE :role MEMBER OF u.roles
            ORDER BY u.createdAt DESC
            """)
    List<User> findByRoleOrderByCreatedAtDesc(@Param("role") Role role);

    @Query("""
            SELECT COUNT(u) FROM User u
            WHERE :role MEMBER OF u.roles AND u.isActive = :active
            """)
    long countByRoleAndActive(@Param("role") Role role, @Param("active") boolean active);

    @Query("""
            SELECT COUNT(u) FROM User u
            WHERE SIZE(u.roles) > 1
            """)
    long countMultiRoleUsers();

    @Query("""
            SELECT u FROM User u
            WHERE u.createdAt >= :from
            ORDER BY u.createdAt DESC
            """)
    List<User> findCreatedSince(@Param("from") LocalDateTime from);

    @Query("""
            SELECT u FROM User u
            WHERE :role MEMBER OF u.roles AND u.isActive = true
            """)
    List<User> findActiveByRole(@Param("role") Role role);
}