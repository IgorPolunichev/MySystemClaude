package com.company.acts.directory.adapter.out.persistence;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/** Spring Data-репозиторий; выборки учитывают мягкое удаление (deletedAt is null). */
public interface EquipmentJpaRepository extends JpaRepository<EquipmentJpaEntity, UUID> {
    Optional<EquipmentJpaEntity> findByIdAndDeletedAtIsNull(UUID id);

    Optional<EquipmentJpaEntity> findFirstByOrganizationIdAndSerialNumberAndDeletedAtIsNull(UUID organizationId, String serial);

    Optional<EquipmentJpaEntity> findFirstByOrganizationIdAndGarageNumberAndDeletedAtIsNull(UUID organizationId, String garage);

    Optional<EquipmentJpaEntity> findFirstByOrganizationIdAndPlateNumberAndDeletedAtIsNull(UUID organizationId, String plate);

    Optional<EquipmentJpaEntity> findFirstBySerialNumberIgnoreCaseAndDeletedAtIsNull(String serial);

    Optional<EquipmentJpaEntity> findFirstBySiteIdAndGarageNumberIgnoreCaseAndDeletedAtIsNull(UUID siteId, String garage);

    Optional<EquipmentJpaEntity> findFirstByOrganizationIdAndSiteIdIsNullAndGarageNumberIgnoreCaseAndDeletedAtIsNull(
            UUID organizationId, String garage);

    boolean existsByOrganizationIdAndDeletedAtIsNull(UUID organizationId);

    boolean existsByDepartmentIdAndDeletedAtIsNull(UUID departmentId);

    boolean existsByEquipmentTypeIdAndDeletedAtIsNull(UUID typeId);
}
