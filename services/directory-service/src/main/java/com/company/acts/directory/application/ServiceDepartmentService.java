package com.company.acts.directory.application;

import com.company.acts.common.core.ConflictException;
import com.company.acts.common.core.NotFoundException;
import com.company.acts.common.core.Uuid7;
import com.company.acts.directory.application.command.NamedCommand;
import com.company.acts.directory.application.port.out.EngineerRepository;
import com.company.acts.directory.application.port.out.ServiceDepartmentRepository;
import com.company.acts.directory.domain.ServiceDepartment;
import java.util.List;
import java.util.UUID;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ServiceDepartmentService {

    private final ServiceDepartmentRepository departments;
    private final EngineerRepository engineers;

    public ServiceDepartmentService(ServiceDepartmentRepository departments, EngineerRepository engineers) {
        this.departments = departments;
        this.engineers = engineers;
    }

    @Transactional(readOnly = true)
    public List<ServiceDepartment> list() {
        return departments.findAll();
    }

    @Transactional(readOnly = true)
    public ServiceDepartment get(UUID id) {
        return departments.findById(id).orElseThrow(() -> NotFoundException.of("Отдел", id));
    }

    @PreAuthorize(Roles.ADMIN)
    @Transactional
    public ServiceDepartment create(NamedCommand command) {
        if (command.id() != null) {
            var existing = departments.findById(command.id());
            if (existing.isPresent()) {
                return existing.get();
            }
        }
        ServiceDepartment department = new ServiceDepartment(command.id() != null ? command.id() : Uuid7.next(), null,
                command.name());
        ensureNameIsFree(department);
        return departments.save(department);
    }

    @PreAuthorize(Roles.ADMIN)
    @Transactional
    public ServiceDepartment update(UUID id, NamedCommand command) {
        ServiceDepartment current = get(id);
        Versions.check(command.version(), current.version());
        ServiceDepartment department = new ServiceDepartment(id, current.version(), command.name());
        ensureNameIsFree(department);
        return departments.save(department);
    }

    @PreAuthorize(Roles.ADMIN)
    @Transactional
    public void delete(UUID id) {
        get(id);
        if (engineers.existsByServiceDepartment(id)) {
            throw new ConflictException("Нельзя удалить отдел: в нём числятся инженеры.");
        }
        departments.delete(id);
    }

    private void ensureNameIsFree(ServiceDepartment department) {
        departments.findByNameIgnoreCase(department.name())
                .filter(other -> !other.id().equals(department.id()))
                .ifPresent(other -> {
                    throw new ConflictException("Отдел «" + other.name() + "» уже есть");
                });
    }
}
