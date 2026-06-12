package com.nemal.service;

import com.nemal.dto.DepartmentDto;
import com.nemal.repository.DepartmentRepository;
import org.springframework.stereotype.Service;

@Service
public class DepartmentService {
    DepartmentRepository departmentRepository;

    private DepartmentService(DepartmentRepository departmentRepository) {
        this.departmentRepository = departmentRepository;
    }
    public DepartmentDto getDepartmentByName(String Name) {
        return DepartmentDto.from(departmentRepository.findByNameIgnoreCase( Name));
    }



}
