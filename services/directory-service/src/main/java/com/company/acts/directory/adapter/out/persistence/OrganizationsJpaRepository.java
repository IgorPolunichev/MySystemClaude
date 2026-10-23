package com.company.acts.directory.adapter.out.persistence;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/** Spring Data-репозиторий; выборки учитывают мягкое удаление (deletedAt is null). */
public interface OrganizationsJpaRepository extends JpaRepository<OrganizationJpaEntity, UUID> {
    Optional<OrganizationJpaEntity> findByIdAndDeletedAtIsNull(UUID id);

    Optional<OrganizationJpaEntity> findFirstByInnAndDeletedAtIsNull(String inn);

    Optional<OrganizationJpaEntity> findFirstByNameIgnoreCaseAndDeletedAtIsNull(String name);

    Optional<OrganizationJpaEntity> findFirstByShortNameIgnoreCaseAndDeletedAtIsNull(String shortName);
}
