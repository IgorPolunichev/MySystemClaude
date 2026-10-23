package com.company.acts.directory.adapter.in.web;

import com.company.acts.directory.adapter.in.web.DirectoryResponses.NamedResponse;
import com.company.acts.directory.application.EquipmentTypeService;
import com.company.acts.directory.application.ServiceDepartmentService;
import com.company.acts.directory.application.command.NamedCommand;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Справочники из одного названия: типы техники и отделы сервисной компании. */
final class NamedDirectoryControllers {

    private NamedDirectoryControllers() {
    }

    @RestController
    @RequestMapping("/api/v1/equipment-types")
    static class EquipmentTypes {

        private final EquipmentTypeService service;

        EquipmentTypes(EquipmentTypeService service) {
            this.service = service;
        }

        @GetMapping
        List<NamedResponse> list() {
            return service.list().stream().map(NamedResponse::of).toList();
        }

        @PostMapping
        @ResponseStatus(HttpStatus.CREATED)
        NamedResponse create(@Valid @RequestBody NamedCommand command) {
            return NamedResponse.of(service.create(command));
        }

        @PutMapping("/{id}")
        NamedResponse update(@PathVariable UUID id, @Valid @RequestBody NamedCommand command) {
            return NamedResponse.of(service.update(id, command));
        }

        @DeleteMapping("/{id}")
        @ResponseStatus(HttpStatus.NO_CONTENT)
        void delete(@PathVariable UUID id) {
            service.delete(id);
        }
    }

    @RestController
    @RequestMapping("/api/v1/service-departments")
    static class ServiceDepartments {

        private final ServiceDepartmentService service;

        ServiceDepartments(ServiceDepartmentService service) {
            this.service = service;
        }

        @GetMapping
        List<NamedResponse> list() {
            return service.list().stream().map(NamedResponse::of).toList();
        }

        @PostMapping
        @ResponseStatus(HttpStatus.CREATED)
        NamedResponse create(@Valid @RequestBody NamedCommand command) {
            return NamedResponse.of(service.create(command));
        }

        @PutMapping("/{id}")
        NamedResponse update(@PathVariable UUID id, @Valid @RequestBody NamedCommand command) {
            return NamedResponse.of(service.update(id, command));
        }

        @DeleteMapping("/{id}")
        @ResponseStatus(HttpStatus.NO_CONTENT)
        void delete(@PathVariable UUID id) {
            service.delete(id);
        }
    }
}
