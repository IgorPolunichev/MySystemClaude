package com.company.acts.directory.adapter.out.persistence;

import com.company.acts.common.core.ConflictException;
import com.company.acts.directory.application.port.out.DepartmentRepository;
import com.company.acts.directory.domain.Department;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
class DepartmentPersistenceAdapter implements DepartmentRepository {

    private final DepartmentsJpaRepository repository;

    DepartmentPersistenceAdapter(DepartmentsJpaRepository repository) {
        this.repository = repository;
    }

    @Override
    public Optional<Department> findById(UUID id) {
        return repository.findByIdAndDeletedAtIsNull(id).map(DepartmentPersistenceAdapter::toDomain);
    }

    @Override
    public List<Department> findByOrganization(UUID organizationId) {
        return repository.findByOrganizationIdAndDeletedAtIsNullOrderByNameAsc(organizationId).stream()
                .map(DepartmentPersistenceAdapter::toDomain).toList();
    }

    @Override
    public Department save(Department d) {
        DepartmentJpaEntity entity = repository.findById(d.id()).orElseGet(() -> {
            DepartmentJpaEntity created = new DepartmentJpaEntity();
            created.setId(d.id());
            return created;
        });
        if (d.version() != null && !Objects.equals(d.version(), entity.getVersion())) {
            throw ConflictException.staleVersion();
        }
        entity.setOrganizationId(d.organizationId());
        entity.setParentId(d.parentId());
        entity.setName(d.name());
        entity.setCode(d.code());
        return toDomain(repository.saveAndFlush(entity));
    }

    @Override
    public void delete(UUID id) {
        repository.findByIdAndDeletedAtIsNull(id).ifPresent(e -> {
            e.markDeleted();
            repository.saveAndFlush(e);
        });
    }

    static Department toDomain(DepartmentJpaEntity e) {
        return new Department(e.getId(), e.getVersion(), e.getOrganizationId(), e.getParentId(), e.getName(), e.getCode());
    }
}
