package com.company.acts.directory.application;

import com.company.acts.common.core.BusinessRuleException;
import com.company.acts.common.core.ConflictException;
import com.company.acts.common.core.NotFoundException;
import com.company.acts.common.core.PageQuery;
import com.company.acts.common.core.PageResult;
import com.company.acts.common.core.Uuid7;
import com.company.acts.directory.application.command.EquipmentCommand;
import com.company.acts.directory.application.port.out.DepartmentRepository;
import com.company.acts.directory.application.port.out.EquipmentRepository;
import com.company.acts.directory.application.port.out.EquipmentTypeRepository;
import com.company.acts.directory.application.port.out.OrganizationRepository;
import com.company.acts.directory.application.view.EquipmentView;
import com.company.acts.directory.domain.Department;
import com.company.acts.directory.domain.DepartmentTree;
import com.company.acts.directory.domain.Equipment;
import java.util.Set;
import java.util.UUID;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Сценарии работы с техникой заказчиков. */
@Service
public class EquipmentService {

    private final EquipmentRepository equipment;
    private final OrganizationRepository organizations;
    private final DepartmentRepository departments;
    private final EquipmentTypeRepository types;
    private final com.company.acts.directory.application.port.out.SiteRepository sites;

    public EquipmentService(EquipmentRepository equipment, OrganizationRepository organizations,
                            DepartmentRepository departments, EquipmentTypeRepository types,
                            com.company.acts.directory.application.port.out.SiteRepository sites) {
        this.equipment = equipment;
        this.organizations = organizations;
        this.departments = departments;
        this.types = types;
        this.sites = sites;
    }

    /** Фильтр по подразделению включает все вложенные в него подразделения. */
    @Transactional(readOnly = true)
    public PageResult<EquipmentView> search(String text, UUID organizationId, UUID departmentId, UUID typeId,
                                            PageQuery page) {
        Set<UUID> departmentIds = null;
        if (departmentId != null) {
            Department department = departments.findById(departmentId)
                    .orElseThrow(() -> NotFoundException.of("Подразделение", departmentId));
            departmentIds = new DepartmentTree(departments.findByOrganization(department.organizationId()))
                    .subtree(departmentId);
        }
        return equipment.search(text, organizationId, departmentIds, typeId, page);
    }

    @Transactional(readOnly = true)
    public EquipmentView get(UUID id) {
        return equipment.findViewById(id).orElseThrow(() -> NotFoundException.of("Техника", id));
    }

    @Transactional
    public EquipmentView create(EquipmentCommand command) {
        if (command.id() != null && equipment.findById(command.id()).isPresent()) {
            return get(command.id());
        }
        Equipment item = toDomain(command.id() != null ? command.id() : Uuid7.next(), null, command);
        validate(item, null);
        equipment.save(item);
        return get(item.id());
    }

    @PreAuthorize(Roles.CAN_EDIT)
    @Transactional
    public EquipmentView update(UUID id, EquipmentCommand command) {
        Equipment current = equipment.findById(id).orElseThrow(() -> NotFoundException.of("Техника", id));
        Versions.check(command.version(), current.version());
        Equipment item = toDomain(id, current.version(), command);
        validate(item, current.siteId());
        equipment.save(item);
        return get(id);
    }

    /**
     * Поднять текущую наработку по данным акта (раздел 2 SPEC.md). Доступно любому пользователю, но только
     * увеличивает значения — уменьшить наработку так нельзя.
     */
    @Transactional
    public void raiseMeter(UUID id, java.math.BigDecimal hours, java.math.BigDecimal km) {
        Equipment current = equipment.findById(id).orElseThrow(() -> NotFoundException.of("Техника", id));
        java.math.BigDecimal newHours = max(current.currentHours(), hours);
        java.math.BigDecimal newKm = max(current.currentKm(), km);
        if (java.util.Objects.equals(newHours, current.currentHours())
                && java.util.Objects.equals(newKm, current.currentKm())) {
            return;
        }
        equipment.save(new Equipment(id, current.version(), current.organizationId(), current.departmentId(),
                current.siteId(), current.equipmentTypeId(), current.brand(), current.model(), current.serialNumber(),
                current.garageNumber(), current.plateNumber(), current.meterType(), newHours, newKm, current.year(),
                current.notes()));
    }

    private static java.math.BigDecimal max(java.math.BigDecimal current, java.math.BigDecimal candidate) {
        if (candidate == null) {
            return current;
        }
        return current == null || candidate.compareTo(current) > 0 ? candidate : current;
    }

    @PreAuthorize(Roles.CAN_EDIT)
    @Transactional
    public void delete(UUID id) {
        equipment.findById(id).orElseThrow(() -> NotFoundException.of("Техника", id));
        equipment.delete(id);
    }

    /** previousSiteId — площадка до изменения: уже назначенную заблокированную площадку можно оставить. */
    private void validate(Equipment item, UUID previousSiteId) {
        if (item.siteId() != null) {
            var site = sites.findById(item.siteId())
                    .orElseThrow(() -> new BusinessRuleException("siteId", "Площадка не найдена"));
            if (!site.organizationId().equals(item.organizationId())) {
                throw new BusinessRuleException("siteId", "Площадка относится к другой организации");
            }
            if (!site.active() && !item.siteId().equals(previousSiteId)) {
                throw new BusinessRuleException("siteId", "Площадка «" + site.name() + "» заблокирована");
            }
        }
        organizations.findById(item.organizationId())
                .orElseThrow(() -> new BusinessRuleException("organizationId", "Организация не найдена"));
        types.findById(item.equipmentTypeId())
                .orElseThrow(() -> new BusinessRuleException("equipmentTypeId", "Тип техники не найден"));
        if (item.departmentId() != null) {
            Department department = departments.findById(item.departmentId())
                    .orElseThrow(() -> new BusinessRuleException("departmentId", "Подразделение не найдено"));
            if (!department.organizationId().equals(item.organizationId())) {
                throw new BusinessRuleException("departmentId", "Подразделение относится к другой организации");
            }
        }
        if (item.serialNumber() != null) {
            equipment.findBySerial(item.organizationId(), item.serialNumber())
                    .filter(other -> !other.id().equals(item.id()))
                    .ifPresent(other -> {
                        throw new ConflictException("У этой организации уже есть техника с серийным номером "
                                + item.serialNumber() + ": " + other.brand() + " " + other.model());
                    });
        }
    }

    static Equipment toDomain(UUID id, Long version, EquipmentCommand c) {
        return new Equipment(id, version, c.organizationId(), c.departmentId(), c.siteId(), c.equipmentTypeId(), c.brand(),
                c.model(), c.serialNumber(), c.garageNumber(), c.plateNumber(), c.meterType(), c.currentHours(),
                c.currentKm(), c.year(), c.notes());
    }
}
