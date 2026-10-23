package com.company.acts.directory.adapter.in.web;

import com.company.acts.common.core.PageQuery;
import com.company.acts.common.importing.ImportResult;
import com.company.acts.common.web.paging.PageResponse;
import com.company.acts.directory.adapter.in.web.DirectoryResponses.DepartmentResponse;
import com.company.acts.directory.adapter.in.web.DirectoryResponses.OrganizationResponse;
import com.company.acts.directory.application.DepartmentService;
import com.company.acts.directory.application.DirectoryImportService;
import com.company.acts.directory.application.OrganizationService;
import com.company.acts.directory.application.command.OrganizationCommand;
import jakarta.validation.Valid;
import java.io.IOException;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/v1/organizations")
class OrganizationController {

    private final OrganizationService organizations;
    private final DepartmentService departments;
    private final DirectoryImportService importService;

    OrganizationController(OrganizationService organizations, DepartmentService departments,
                           DirectoryImportService importService) {
        this.organizations = organizations;
        this.departments = departments;
        this.importService = importService;
    }

    @GetMapping
    PageResponse<OrganizationResponse> list(@RequestParam(required = false) String q,
                                            @RequestParam(required = false) Boolean active,
                                            @RequestParam(required = false) Integer page,
                                            @RequestParam(required = false) Integer size,
                                            @RequestParam(required = false) String sort) {
        return DirectoryResponses.page(organizations.search(q, active, PageQuery.of(page, size, sort)),
                OrganizationResponse::of);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    OrganizationResponse create(@Valid @RequestBody OrganizationCommand command) {
        return OrganizationResponse.of(organizations.create(command));
    }

    @GetMapping("/{id}")
    OrganizationResponse get(@PathVariable UUID id) {
        return OrganizationResponse.of(organizations.get(id));
    }

    @PutMapping("/{id}")
    OrganizationResponse update(@PathVariable UUID id, @Valid @RequestBody OrganizationCommand command) {
        return OrganizationResponse.of(organizations.update(id, command));
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void delete(@PathVariable UUID id) {
        organizations.delete(id);
    }

    @GetMapping("/{id}/departments")
    List<DepartmentResponse> departments(@PathVariable UUID id) {
        return departments.listByOrganization(id).stream().map(DepartmentResponse::of).toList();
    }

    @PostMapping("/import")
    ImportResult importFile(@RequestParam("file") MultipartFile file) throws IOException {
        return importService.importOrganizations(file.getOriginalFilename(), file.getBytes());
    }

    @GetMapping("/import/template")
    ResponseEntity<byte[]> template() {
        return DirectoryResponses.xlsx("Шаблон импорта организаций.xlsx", importService.organizationTemplate());
    }
}
