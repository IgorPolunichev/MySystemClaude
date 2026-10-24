package com.company.acts.directory.adapter.in.web;

import com.company.acts.directory.application.ExecutorService;
import com.company.acts.directory.application.SiteService;
import com.company.acts.directory.application.command.ExecutorCommand;
import com.company.acts.directory.application.command.SiteCommand;
import com.company.acts.directory.domain.Executor;
import com.company.acts.directory.domain.Site;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Исполнители и производственные площадки заказчиков. */
final class ExecutorSiteControllers {

    private ExecutorSiteControllers() {
    }

    record ExecutorResponse(UUID id, String name, String shortName, String inn, String kpp, String ogrn,
                            String address, String phone, String email, String bankDetails, boolean isDefault,
                            boolean active, Long version) {
        static ExecutorResponse of(Executor e) {
            return new ExecutorResponse(e.id(), e.name(), e.shortName(), e.inn(), e.kpp(), e.ogrn(), e.address(),
                    e.phone(), e.email(), e.bankDetails(), e.isDefault(), e.active(), e.version());
        }
    }

    record SiteResponse(UUID id, UUID organizationId, String name, String code, String address, boolean active,
                        Long version) {
        static SiteResponse of(Site s) {
            return new SiteResponse(s.id(), s.organizationId(), s.name(), s.code(), s.address(), s.active(), s.version());
        }
    }

    @RestController
    @RequestMapping("/api/v1/executors")
    static class Executors {

        private final ExecutorService service;

        Executors(ExecutorService service) {
            this.service = service;
        }

        @GetMapping
        List<ExecutorResponse> list(@RequestParam(required = false, defaultValue = "false") boolean includeInactive) {
            return service.list(includeInactive).stream().map(ExecutorResponse::of).toList();
        }

        @GetMapping("/default")
        ExecutorResponse getDefault() {
            return ExecutorResponse.of(service.getDefault());
        }

        @GetMapping("/{id}")
        ExecutorResponse get(@PathVariable UUID id) {
            return ExecutorResponse.of(service.get(id));
        }

        @PostMapping
        @ResponseStatus(HttpStatus.CREATED)
        ExecutorResponse create(@Valid @RequestBody ExecutorCommand command) {
            return ExecutorResponse.of(service.create(command));
        }

        @PutMapping("/{id}")
        ExecutorResponse update(@PathVariable UUID id, @Valid @RequestBody ExecutorCommand command) {
            return ExecutorResponse.of(service.update(id, command));
        }
    }

    @RestController
    static class Sites {

        private final SiteService service;

        Sites(SiteService service) {
            this.service = service;
        }

        @GetMapping("/api/v1/organizations/{organizationId}/sites")
        List<SiteResponse> list(@PathVariable UUID organizationId,
                                @RequestParam(required = false, defaultValue = "false") boolean includeInactive) {
            return service.listByOrganization(organizationId, includeInactive).stream().map(SiteResponse::of).toList();
        }

        @GetMapping("/api/v1/sites/{id}")
        SiteResponse get(@PathVariable UUID id) {
            return SiteResponse.of(service.get(id));
        }

        @PostMapping("/api/v1/sites")
        @ResponseStatus(HttpStatus.CREATED)
        SiteResponse create(@Valid @RequestBody SiteCommand command) {
            return SiteResponse.of(service.create(command));
        }

        @PutMapping("/api/v1/sites/{id}")
        SiteResponse update(@PathVariable UUID id, @Valid @RequestBody SiteCommand command) {
            return SiteResponse.of(service.update(id, command));
        }
    }
}
