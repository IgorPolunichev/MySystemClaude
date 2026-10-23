package com.company.acts.directory.application;

import com.company.acts.common.core.BusinessRuleException;
import com.company.acts.common.core.ConflictException;
import com.company.acts.common.core.NotFoundException;
import com.company.acts.common.core.Uuid7;
import com.company.acts.directory.application.command.DepartmentCommand;
import com.company.acts.directory.application.port.out.DepartmentRepository;
import com.company.acts.directory.application.port.out.EquipmentRepository;
import com.company.acts.directory.application.port.out.OrganizationRepository;
import com.company.acts.directory.domain.Department;
import com.company.acts.directory.domain.DepartmentTree;
import java.util.List;
import java.util.UUID;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Сценарии работы с подразделениями заказчика. */
@Service
public class DepartmentService {

    private final DepartmentRepository departments;
    private final OrganizationRepository organizations;
    private final EquipmentRepository equipment;

    public DepartmentService(DepartmentRepository departments, OrganizationRepository organizations,
                             EquipmentRepository equipment) {
        this.departments = departments;
        this.organizations = organizations;
        this.equipment = equipment;
    }

    @Transactional(readOnly = true)
    public List<Department> listByOrganization(UUID organizationId) {
        organizations.findById(organizationId).orElseThrow(() -> NotFoundException.of("Организация", organizationId));
        return departments.findByOrganization(organizationId);
    }

    @Transactional(readOnly = true)
    public Department get(UUID id) {
        return departments.findById(id).orElseThrow(() -> NotFoundException.of("Подразделение", id));
    }

    @Transactional
    public Department create(DepartmentCommand command) {
        if (command.id() != null) {
            var existing = departments.findById(command.id());
            if (existing.isPresent()) {
                return existing.get();
            }
        }
        Department department = new Department(command.id() != null ? command.id() : Uuid7.next(), null,
                command.organizationId(), command.parentId(), command.name(), command.code());
        validateParent(department);
        return departments.save(department);
    }

    @PreAuthorize(Roles.CAN_EDIT)
    @Transactional
    public Department update(UUID id, DepartmentCommand command) {
        Department current = get(id);
        Versions.check(command.version(), current.version());
        if (!current.organizationId().equals(command.organizationId())) {
            throw new BusinessRuleException("organizationId", "Подразделение нельзя перенести в другую организацию");
        }
        Department department = new Department(id, current.version(), current.organizationId(), command.parentId(),
                command.name(), command.code());
        validateParent(department);
        DepartmentTree tree = new DepartmentTree(departments.findByOrganization(current.organizationId()));
        if (tree.wouldCreateCycle(id, department.parentId())) {
            throw new BusinessRuleException("parentId", "Подразделение нельзя вложить в его же дочернее подразделение");
        }
        return departments.save(department);
    }

    @PreAuthorize(Roles.CAN_EDIT)
    @Transactional
    public void delete(UUID id) {
        Department department = get(id);
        if (new DepartmentTree(departments.findByOrganization(department.organizationId())).hasChildren(id)) {
            throw new ConflictException("Нельзя удалить подразделение: в нём есть вложенные подразделения.");
        }
        if (equipment.existsByDepartment(id)) {
            throw new ConflictException("Нельзя удалить подразделение: за ним числится техника.");
        }
        departments.delete(id);
    }

    private void validateParent(Department department) {
        organizations.findById(department.organizationId())
                .orElseThrow(() -> new BusinessRuleException("organizationId", "Организация не найдена"));
        if (department.parentId() != null) {
            Department parent = departments.findById(department.parentId())
                    .orElseThrow(() -> new BusinessRuleException("parentId", "Родительское подразделение не найдено"));
            if (!parent.organizationId().equals(department.organizationId())) {
                throw new BusinessRuleException("parentId", "Родительское подразделение относится к другой организации");
            }
        }
    }
}
