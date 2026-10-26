package com.company.acts.directory.adapter.in.web;

import com.company.acts.common.core.PageQuery;
import com.company.acts.common.importing.ImportResult;
import com.company.acts.common.web.paging.PageResponse;
import com.company.acts.directory.application.DirectoryImportService;
import com.company.acts.directory.application.EquipmentService;
import com.company.acts.directory.application.command.EquipmentCommand;
import com.company.acts.directory.application.command.MeterCommand;
import com.company.acts.directory.application.view.EquipmentView;
import jakarta.validation.Valid;
import java.io.IOException;
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
@RequestMapping("/api/v1/equipment")
class EquipmentController {

    private final EquipmentService equipment;
    private final DirectoryImportService importService;

    EquipmentController(EquipmentService equipment, DirectoryImportService importService) {
        this.equipment = equipment;
        this.importService = importService;
    }

    @GetMapping
    PageResponse<EquipmentView> list(@RequestParam(required = false) String q,
                                     @RequestParam(required = false) UUID organizationId,
                                     @RequestParam(required = false) UUID departmentId,
                                     @RequestParam(required = false) UUID equipmentTypeId,
                                     @RequestParam(required = false) Integer page,
                                     @RequestParam(required = false) Integer size,
                                     @RequestParam(required = false) String sort) {
        return DirectoryResponses.page(equipment.search(q, organizationId, departmentId, equipmentTypeId,
                PageQuery.of(page, size, sort)), v -> v);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    EquipmentView create(@Valid @RequestBody EquipmentCommand command) {
        return equipment.create(command);
    }

    /** Проверка до сохранения: техника с тем же VIN (везде) или гаражным номером (в подразделении). */
    @GetMapping("/duplicates")
    EquipmentService.Duplicates duplicates(@RequestParam(required = false) String serialNumber,
                                           @RequestParam(required = false) String garageNumber,
                                           @RequestParam(required = false) UUID organizationId,
                                           @RequestParam(required = false) UUID departmentId,
                                           @RequestParam(required = false) UUID excludeId) {
        return equipment.duplicates(serialNumber, garageNumber, organizationId, departmentId, excludeId);
    }

    @GetMapping("/{id}")
    EquipmentView get(@PathVariable UUID id) {
        return equipment.get(id);
    }

    @PutMapping("/{id}")
    EquipmentView update(@PathVariable UUID id, @Valid @RequestBody EquipmentCommand command) {
        return equipment.update(id, command);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void delete(@PathVariable UUID id) {
        equipment.delete(id);
    }

    /** Поднять наработку по данным акта; значения только увеличиваются. */
    @PostMapping("/{id}/meter")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void raiseMeter(@PathVariable UUID id, @Valid @RequestBody MeterCommand command) {
        equipment.raiseMeter(id, command.hours(), command.km());
    }

    @PostMapping("/import")
    ImportResult importFile(@RequestParam("file") MultipartFile file) throws IOException {
        return importService.importEquipment(file.getOriginalFilename(), file.getBytes());
    }

    @GetMapping("/import/template")
    ResponseEntity<byte[]> template() {
        return DirectoryResponses.xlsx("Шаблон импорта техники.xlsx", importService.equipmentTemplate());
    }
}
