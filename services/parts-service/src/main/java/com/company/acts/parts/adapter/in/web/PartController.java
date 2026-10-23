package com.company.acts.parts.adapter.in.web;

import com.company.acts.common.core.PageQuery;
import com.company.acts.common.importing.ImportResult;
import com.company.acts.common.web.paging.PageResponse;
import com.company.acts.parts.application.PartImportService;
import com.company.acts.parts.application.PartService;
import com.company.acts.parts.application.command.PartCommand;
import com.company.acts.parts.domain.Part;
import com.company.acts.parts.domain.Unit;
import jakarta.validation.Valid;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
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
@RequestMapping("/api/v1")
class PartController {

    private final PartService parts;
    private final PartImportService importService;

    PartController(PartService parts, PartImportService importService) {
        this.parts = parts;
        this.importService = importService;
    }

    record PartResponse(UUID id, String name, String catalogNumber, String manufacturer, String defaultUnit,
                        boolean fluid, String notes, Long version) {
        static PartResponse of(Part p) {
            return new PartResponse(p.id(), p.name(), p.catalogNumber(), p.manufacturer(), p.defaultUnit(), p.fluid(),
                    p.notes(), p.version());
        }
    }

    @GetMapping("/units")
    List<Unit> units() {
        return parts.units();
    }

    @GetMapping("/parts")
    PageResponse<PartResponse> list(@RequestParam(required = false) String q,
                                    @RequestParam(required = false) Integer page,
                                    @RequestParam(required = false) Integer size,
                                    @RequestParam(required = false) String sort) {
        var result = parts.search(q, PageQuery.of(page, size, sort));
        return new PageResponse<>(result.items().stream().map(PartResponse::of).toList(), result.page(),
                result.size(), result.totalElements());
    }

    @PostMapping("/parts")
    @ResponseStatus(HttpStatus.CREATED)
    PartResponse create(@Valid @RequestBody PartCommand command) {
        return PartResponse.of(parts.create(command));
    }

    @GetMapping("/parts/{id}")
    PartResponse get(@PathVariable UUID id) {
        return PartResponse.of(parts.get(id));
    }

    @PutMapping("/parts/{id}")
    PartResponse update(@PathVariable UUID id, @Valid @RequestBody PartCommand command) {
        return PartResponse.of(parts.update(id, command));
    }

    @DeleteMapping("/parts/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void delete(@PathVariable UUID id) {
        parts.delete(id);
    }

    @PostMapping("/parts/import")
    ImportResult importFile(@RequestParam("file") MultipartFile file) throws IOException {
        return importService.importParts(file.getOriginalFilename(), file.getBytes());
    }

    @GetMapping("/parts/import/template")
    ResponseEntity<byte[]> template() {
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                        .filename("Шаблон импорта запчастей.xlsx", StandardCharsets.UTF_8).build().toString())
                .body(importService.template());
    }
}
