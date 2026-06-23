package com.nemal.service;

import com.nemal.dto.CreateDepartmentDto;
import com.nemal.dto.DepartmentDto;
import com.nemal.entity.Department;
import com.nemal.repository.DepartmentRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class DepartmentService {

    private final DepartmentRepository departmentRepository;

    public DepartmentService(DepartmentRepository departmentRepository) {
        this.departmentRepository = departmentRepository;
    }

    public List<DepartmentDto> getAllDepartments() {
        return departmentRepository.findAll().stream()
                .sorted(Comparator.comparing(Department::getName, String.CASE_INSENSITIVE_ORDER))
                .map(DepartmentDto::from)
                .collect(Collectors.toList());
    }

    public DepartmentDto getDepartmentByName(String name) {
        Department department = departmentRepository.findByNameIgnoreCase(name);
        if (department == null) {
            throw new IllegalArgumentException("Department not found: " + name);
        }
        return DepartmentDto.from(department);
    }

    @Transactional
    public DepartmentDto createDepartment(CreateDepartmentDto dto) {
        if (dto.name() == null || dto.name().isBlank()) {
            throw new IllegalArgumentException("Department name is required");
        }

        String name = dto.name().trim();
        if (departmentRepository.existsByNameIgnoreCase(name)) {
            throw new IllegalArgumentException("Department already exists: " + name);
        }

        String code = resolveDepartmentCode(dto.code(), name);
        if (departmentRepository.existsByCodeIgnoreCase(code)) {
            throw new IllegalArgumentException("Department code already exists: " + code);
        }

        Department department = Department.builder()
                .name(name)
                .code(code)
                .build();

        return DepartmentDto.from(departmentRepository.save(department));
    }

    private String resolveDepartmentCode(String requestedCode, String name) {
        if (requestedCode != null && !requestedCode.isBlank()) {
            return requestedCode.trim().toUpperCase();
        }

        String base = name.replaceAll("[^A-Za-z0-9]", "").toUpperCase();
        if (base.length() < 3) {
            base = (base + "DEPT").substring(0, Math.min(4, base.length() + 4));
        }

        String candidate = base.substring(0, Math.min(4, base.length()));
        if (!departmentRepository.existsByCodeIgnoreCase(candidate)) {
            return candidate;
        }

        for (int i = 2; i < 100; i++) {
            String suffix = String.valueOf(i);
            String next = candidate.substring(0, Math.min(candidate.length(), 4 - suffix.length())) + suffix;
            if (!departmentRepository.existsByCodeIgnoreCase(next)) {
                return next;
            }
        }

        throw new IllegalArgumentException("Unable to generate a unique department code");
    }
}
