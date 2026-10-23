package com.company.acts.directory.adapter.in.web;

import com.company.acts.directory.adapter.in.web.DirectoryResponses.DepartmentResponse;
import com.company.acts.directory.application.DepartmentService;
import com.company.acts.directory.application.command.DepartmentCommand;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/departments")
class DepartmentController {

    private final DepartmentService departments;

    DepartmentController(DepartmentService departments) {
        this.departments = departments;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    DepartmentResponse create(@Valid @RequestBody DepartmentCommand command) {
        return DepartmentResponse.of(departments.create(command));
    }

    @PutMapping("/{id}")
    DepartmentResponse update(@PathVariable UUID id, @Valid @RequestBody DepartmentCommand command) {
        return DepartmentResponse.of(departments.update(id, command));
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void delete(@PathVariable UUID id) {
        departments.delete(id);
    }
}
