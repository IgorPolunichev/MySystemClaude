package com.company.acts.directory.adapter.out.persistence;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/** Spring Data-репозиторий; выборки учитывают мягкое удаление (deletedAt is null). */
public interface EquipmentTypesJpaRepository extends JpaRepository<EquipmentTypeJpaEntity, UUID> {
    Optional<EquipmentTypeJpaEntity> findByIdAndDeletedAtIsNull(UUID id);

    Optional<EquipmentTypeJpaEntity> findFirstByNameIgnoreCaseAndDeletedAtIsNull(String name);

    List<EquipmentTypeJpaEntity> findByDeletedAtIsNullOrderByNameAsc();
}
