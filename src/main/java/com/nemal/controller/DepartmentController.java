package com.nemal.controller;

import com.nemal.dto.DepartmentDto;
import com.nemal.service.DepartmentService;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;


@RestController
@RequestMapping("/api/department")
public class DepartmentController {
    private final DepartmentService departmentService;

    public DepartmentController(DepartmentService departmentService) {
        this.departmentService = departmentService;
    }

    @GetMapping("/{DepartmentName}")
    public ResponseEntity<DepartmentDto> getDepartmentByName(@PathVariable String DepartmentName) {
        return ResponseEntity.ok(departmentService.getDepartmentByName(DepartmentName));
    }





}
