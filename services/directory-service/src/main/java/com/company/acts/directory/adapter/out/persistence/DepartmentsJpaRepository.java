package com.company.acts.directory.adapter.out.persistence;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/** Spring Data-репозиторий; выборки учитывают мягкое удаление (deletedAt is null). */
public interface DepartmentsJpaRepository extends JpaRepository<DepartmentJpaEntity, UUID> {
    Optional<DepartmentJpaEntity> findByIdAndDeletedAtIsNull(UUID id);

    List<DepartmentJpaEntity> findByOrganizationIdAndDeletedAtIsNullOrderByNameAsc(UUID organizationId);
}
