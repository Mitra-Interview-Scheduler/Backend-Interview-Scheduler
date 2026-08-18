package com.nemal.repository;

import com.nemal.entity.Department;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface DepartmentRepository extends JpaRepository<Department, Long> {

    Department findByNameIgnoreCase(String name);

    boolean existsByNameIgnoreCase(String name);

    boolean existsByCodeIgnoreCase(String code);

    List<Department> findByIsActiveTrue();
}