package com.company.acts.directory.adapter.in.web;

import com.company.acts.common.core.PageQuery;
import com.company.acts.common.web.paging.PageResponse;
import com.company.acts.directory.application.EngineerService;
import com.company.acts.directory.application.command.EngineerCreateCommand;
import com.company.acts.directory.application.command.EngineerUpdateCommand;
import com.company.acts.directory.application.view.EngineerView;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/engineers")
class EngineerController {

    private final EngineerService engineers;

    EngineerController(EngineerService engineers) {
        this.engineers = engineers;
    }

    @GetMapping
    PageResponse<EngineerView> list(@RequestParam(required = false) String q,
                                    @RequestParam(required = false) UUID serviceDepartmentId,
                                    @RequestParam(required = false) Boolean active,
                                    @RequestParam(required = false) Integer page,
                                    @RequestParam(required = false) Integer size,
                                    @RequestParam(required = false) String sort) {
        return DirectoryResponses.page(engineers.search(q, serviceDepartmentId, active,
                PageQuery.of(page, size, sort)), v -> v);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    EngineerView create(@Valid @RequestBody EngineerCreateCommand command) {
        return engineers.create(command);
    }

    @GetMapping("/me")
    EngineerView me(@AuthenticationPrincipal Jwt jwt) {
        return engineers.current(jwt.getSubject(), jwt.getClaimAsString("preferred_username"));
    }

    @GetMapping("/{id}")
    EngineerView get(@PathVariable UUID id) {
        return engineers.get(id);
    }

    @PutMapping("/{id}")
    EngineerView update(@PathVariable UUID id, @Valid @RequestBody EngineerUpdateCommand command) {
        return engineers.update(id, command);
    }
}
