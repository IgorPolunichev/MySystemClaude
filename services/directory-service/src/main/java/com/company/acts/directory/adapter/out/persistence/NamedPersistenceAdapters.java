package com.company.acts.directory.adapter.out.persistence;

import com.company.acts.common.core.ConflictException;
import com.company.acts.directory.application.port.out.EquipmentTypeRepository;
import com.company.acts.directory.application.port.out.ServiceDepartmentRepository;
import com.company.acts.directory.domain.EquipmentType;
import com.company.acts.directory.domain.ServiceDepartment;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Component;

/** Адаптеры справочников из одного названия: типы техники и отделы сервисной компании. */
final class NamedPersistenceAdapters {

    private NamedPersistenceAdapters() {
    }

    @Component
    static class EquipmentTypes implements EquipmentTypeRepository {

        private final EquipmentTypesJpaRepository repository;

        EquipmentTypes(EquipmentTypesJpaRepository repository) {
            this.repository = repository;
        }

        @Override
        public Optional<EquipmentType> findById(UUID id) {
            return repository.findByIdAndDeletedAtIsNull(id).map(EquipmentTypes::toDomain);
        }

        @Override
        public Optional<EquipmentType> findByNameIgnoreCase(String name) {
            return repository.findFirstByNameIgnoreCaseAndDeletedAtIsNull(name).map(EquipmentTypes::toDomain);
        }

        @Override
        public List<EquipmentType> findAll() {
            return repository.findByDeletedAtIsNullOrderByNameAsc().stream().map(EquipmentTypes::toDomain).toList();
        }

        @Override
        public EquipmentType save(EquipmentType t) {
            EquipmentTypeJpaEntity entity = repository.findById(t.id()).orElseGet(() -> {
                EquipmentTypeJpaEntity created = new EquipmentTypeJpaEntity();
                created.setId(t.id());
                return created;
            });
            if (t.version() != null && !Objects.equals(t.version(), entity.getVersion())) {
                throw ConflictException.staleVersion();
            }
            entity.setName(t.name());
            return toDomain(repository.saveAndFlush(entity));
        }

        @Override
        public void delete(UUID id) {
            repository.findByIdAndDeletedAtIsNull(id).ifPresent(e -> {
                e.markDeleted();
                repository.saveAndFlush(e);
            });
        }

        static EquipmentType toDomain(EquipmentTypeJpaEntity e) {
            return new EquipmentType(e.getId(), e.getVersion(), e.getName());
        }
    }

    @Component
    static class ServiceDepartments implements ServiceDepartmentRepository {

        private final ServiceDepartmentsJpaRepository repository;

        ServiceDepartments(ServiceDepartmentsJpaRepository repository) {
            this.repository = repository;
        }

        @Override
        public Optional<ServiceDepartment> findById(UUID id) {
            return repository.findByIdAndDeletedAtIsNull(id).map(ServiceDepartments::toDomain);
        }

        @Override
        public Optional<ServiceDepartment> findByNameIgnoreCase(String name) {
            return repository.findFirstByNameIgnoreCaseAndDeletedAtIsNull(name).map(ServiceDepartments::toDomain);
        }

        @Override
        public List<ServiceDepartment> findAll() {
            return repository.findByDeletedAtIsNullOrderByNameAsc().stream().map(ServiceDepartments::toDomain).toList();
        }

        @Override
        public ServiceDepartment save(ServiceDepartment d) {
            ServiceDepartmentJpaEntity entity = repository.findById(d.id()).orElseGet(() -> {
                ServiceDepartmentJpaEntity created = new ServiceDepartmentJpaEntity();
                created.setId(d.id());
                return created;
            });
            if (d.version() != null && !Objects.equals(d.version(), entity.getVersion())) {
                throw ConflictException.staleVersion();
            }
            entity.setName(d.name());
            return toDomain(repository.saveAndFlush(entity));
        }

        @Override
        public void delete(UUID id) {
            repository.findByIdAndDeletedAtIsNull(id).ifPresent(e -> {
                e.markDeleted();
                repository.saveAndFlush(e);
            });
        }

        static ServiceDepartment toDomain(ServiceDepartmentJpaEntity e) {
            return new ServiceDepartment(e.getId(), e.getVersion(), e.getName());
        }
    }
}
