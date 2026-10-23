package com.company.acts.directory.adapter.out.persistence;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/** Spring Data-репозиторий; выборки учитывают мягкое удаление (deletedAt is null). */
public interface ServiceDepartmentsJpaRepository extends JpaRepository<ServiceDepartmentJpaEntity, UUID> {
    Optional<ServiceDepartmentJpaEntity> findByIdAndDeletedAtIsNull(UUID id);

    Optional<ServiceDepartmentJpaEntity> findFirstByNameIgnoreCaseAndDeletedAtIsNull(String name);

    List<ServiceDepartmentJpaEntity> findByDeletedAtIsNullOrderByNameAsc();
}
