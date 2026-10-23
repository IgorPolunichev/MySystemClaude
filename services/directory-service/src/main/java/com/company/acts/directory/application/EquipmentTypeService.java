package com.company.acts.directory.application;

import com.company.acts.common.core.ConflictException;
import com.company.acts.common.core.NotFoundException;
import com.company.acts.common.core.Uuid7;
import com.company.acts.directory.application.command.NamedCommand;
import com.company.acts.directory.application.port.out.EquipmentRepository;
import com.company.acts.directory.application.port.out.EquipmentTypeRepository;
import com.company.acts.directory.domain.EquipmentType;
import java.util.List;
import java.util.UUID;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class EquipmentTypeService {

    private final EquipmentTypeRepository types;
    private final EquipmentRepository equipment;

    public EquipmentTypeService(EquipmentTypeRepository types, EquipmentRepository equipment) {
        this.types = types;
        this.equipment = equipment;
    }

    @Transactional(readOnly = true)
    public List<EquipmentType> list() {
        return types.findAll();
    }

    @Transactional(readOnly = true)
    public EquipmentType get(UUID id) {
        return types.findById(id).orElseThrow(() -> NotFoundException.of("Тип техники", id));
    }

    @Transactional
    public EquipmentType create(NamedCommand command) {
        if (command.id() != null) {
            var existing = types.findById(command.id());
            if (existing.isPresent()) {
                return existing.get();
            }
        }
        EquipmentType type = new EquipmentType(command.id() != null ? command.id() : Uuid7.next(), null, command.name());
        ensureNameIsFree(type);
        return types.save(type);
    }

    /** Найти тип по названию или создать новый — для импорта техники. */
    @Transactional
    public EquipmentType findOrCreate(String name) {
        return types.findByNameIgnoreCase(name.strip())
                .orElseGet(() -> types.save(new EquipmentType(Uuid7.next(), null, name)));
    }

    @PreAuthorize(Roles.CAN_EDIT)
    @Transactional
    public EquipmentType update(UUID id, NamedCommand command) {
        EquipmentType current = get(id);
        Versions.check(command.version(), current.version());
        EquipmentType type = new EquipmentType(id, current.version(), command.name());
        ensureNameIsFree(type);
        return types.save(type);
    }

    @PreAuthorize(Roles.CAN_EDIT)
    @Transactional
    public void delete(UUID id) {
        get(id);
        if (equipment.existsByType(id)) {
            throw new ConflictException("Нельзя удалить тип: есть техника этого типа.");
        }
        types.delete(id);
    }

    private void ensureNameIsFree(EquipmentType type) {
        types.findByNameIgnoreCase(type.name())
                .filter(other -> !other.id().equals(type.id()))
                .ifPresent(other -> {
                    throw new ConflictException("Тип техники «" + other.name() + "» уже есть");
                });
    }
}
