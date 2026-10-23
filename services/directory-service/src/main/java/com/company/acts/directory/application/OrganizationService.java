package com.company.acts.directory.application;

import com.company.acts.common.core.ConflictException;
import com.company.acts.common.core.NotFoundException;
import com.company.acts.common.core.PageQuery;
import com.company.acts.common.core.PageResult;
import com.company.acts.common.core.Uuid7;
import com.company.acts.directory.application.command.OrganizationCommand;
import com.company.acts.directory.application.port.out.DepartmentRepository;
import com.company.acts.directory.application.port.out.EquipmentRepository;
import com.company.acts.directory.application.port.out.OrganizationRepository;
import com.company.acts.directory.domain.Organization;
import java.util.UUID;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Сценарии работы с организациями-заказчиками. */
@Service
public class OrganizationService {

    private final OrganizationRepository organizations;
    private final DepartmentRepository departments;
    private final EquipmentRepository equipment;

    public OrganizationService(OrganizationRepository organizations, DepartmentRepository departments,
                               EquipmentRepository equipment) {
        this.organizations = organizations;
        this.departments = departments;
        this.equipment = equipment;
    }

    @Transactional(readOnly = true)
    public PageResult<Organization> search(String text, Boolean active, PageQuery page) {
        return organizations.search(text, active, page);
    }

    @Transactional(readOnly = true)
    public Organization get(UUID id) {
        return organizations.findById(id).orElseThrow(() -> NotFoundException.of("Организация", id));
    }

    /** Создание идемпотентно: повтор запроса с тем же id возвращает уже созданную запись. */
    @Transactional
    public Organization create(OrganizationCommand command) {
        if (command.id() != null) {
            var existing = organizations.findById(command.id());
            if (existing.isPresent()) {
                return existing.get();
            }
        }
        Organization organization = toDomain(command.id() != null ? command.id() : Uuid7.next(), null, command);
        ensureInnIsFree(organization);
        return organizations.save(organization);
    }

    @PreAuthorize(Roles.CAN_EDIT)
    @Transactional
    public Organization update(UUID id, OrganizationCommand command) {
        Organization current = get(id);
        Versions.check(command.version(), current.version());
        Organization organization = toDomain(id, current.version(), command);
        ensureInnIsFree(organization);
        return organizations.save(organization);
    }

    @PreAuthorize(Roles.CAN_EDIT)
    @Transactional
    public void delete(UUID id) {
        get(id);
        if (equipment.existsByOrganization(id)) {
            throw new ConflictException("Нельзя удалить организацию: за ней числится техника. Сначала удалите или перенесите её.");
        }
        if (!departments.findByOrganization(id).isEmpty()) {
            throw new ConflictException("Нельзя удалить организацию: у неё есть подразделения. Сначала удалите их.");
        }
        organizations.delete(id);
    }

    private void ensureInnIsFree(Organization organization) {
        if (organization.inn() == null) {
            return;
        }
        organizations.findByInn(organization.inn())
                .filter(other -> !other.id().equals(organization.id()))
                .ifPresent(other -> {
                    throw new ConflictException("Организация с ИНН " + organization.inn() + " уже есть: «"
                            + other.displayName() + "»");
                });
    }

    static Organization toDomain(UUID id, Long version, OrganizationCommand c) {
        return new Organization(id, version, c.name(), c.shortName(), c.inn(), c.kpp(), c.ogrn(), c.legalAddress(),
                c.actualAddress(), c.bankDetails(), c.phone(), c.email(), c.notes(),
                c.active() == null || c.active());
    }
}
