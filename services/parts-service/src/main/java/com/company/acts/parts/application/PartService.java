package com.company.acts.parts.application;

import com.company.acts.common.core.BusinessRuleException;
import com.company.acts.common.core.ConflictException;
import com.company.acts.common.core.NotFoundException;
import com.company.acts.common.core.PageQuery;
import com.company.acts.common.core.PageResult;
import com.company.acts.common.core.Uuid7;
import com.company.acts.parts.application.command.PartCommand;
import com.company.acts.parts.application.port.out.PartRepository;
import com.company.acts.parts.application.port.out.UnitRepository;
import com.company.acts.parts.domain.Part;
import com.company.acts.parts.domain.Unit;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Каталог запчастей: добавлять может любой пользователь, изменять и удалять — руководитель и администратор. */
@Service
public class PartService {

    static final String CAN_EDIT = "hasAnyRole('DEPARTMENT_HEAD', 'ADMIN')";

    private final PartRepository parts;
    private final UnitRepository units;

    public PartService(PartRepository parts, UnitRepository units) {
        this.parts = parts;
        this.units = units;
    }

    @Transactional(readOnly = true)
    public List<Unit> units() {
        return units.findAll();
    }

    @Transactional(readOnly = true)
    public PageResult<Part> search(String text, PageQuery page) {
        return parts.search(text, page);
    }

    @Transactional(readOnly = true)
    public Part get(UUID id) {
        return parts.findById(id).orElseThrow(() -> NotFoundException.of("Запчасть", id));
    }

    @Transactional
    public Part create(PartCommand command) {
        if (command.id() != null) {
            var existing = parts.findById(command.id());
            if (existing.isPresent()) {
                return existing.get();
            }
        }
        Part part = toDomain(command.id() != null ? command.id() : Uuid7.next(), null, command);
        validate(part);
        return parts.save(part);
    }

    @PreAuthorize(CAN_EDIT)
    @Transactional
    public Part update(UUID id, PartCommand command) {
        Part current = get(id);
        if (command.version() == null) {
            throw new BusinessRuleException("version", "Не передана версия записи");
        }
        if (!Objects.equals(command.version(), current.version())) {
            throw ConflictException.staleVersion();
        }
        Part part = toDomain(id, current.version(), command);
        validate(part);
        return parts.save(part);
    }

    @PreAuthorize(CAN_EDIT)
    @Transactional
    public void delete(UUID id) {
        get(id);
        parts.delete(id);
    }

    private void validate(Part part) {
        units.findByCode(part.defaultUnit())
                .orElseThrow(() -> new BusinessRuleException("defaultUnit", "Неизвестная единица измерения: "
                        + part.defaultUnit()));
        if (part.catalogNumber() != null) {
            parts.findByCatalogNumber(part.catalogNumber(), part.manufacturer())
                    .filter(other -> !other.id().equals(part.id()))
                    .ifPresent(other -> {
                        throw new ConflictException("В каталоге уже есть позиция с номером " + part.catalogNumber()
                                + (part.manufacturer() != null ? " (" + part.manufacturer() + ")" : "") + ": "
                                + other.name());
                    });
        }
    }

    /** Жидкость по умолчанию — если единица измерения литры. */
    static Part toDomain(UUID id, Long version, PartCommand c) {
        boolean fluid = c.fluid() != null ? c.fluid() : "L".equalsIgnoreCase(c.defaultUnit() == null ? "" : c.defaultUnit().strip());
        return new Part(id, version, c.name(), c.catalogNumber(), c.manufacturer(), c.defaultUnit(), fluid, c.notes());
    }
}
