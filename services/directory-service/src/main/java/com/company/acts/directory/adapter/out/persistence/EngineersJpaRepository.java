package com.company.acts.directory.adapter.out.persistence;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/** Spring Data-репозиторий; выборки учитывают мягкое удаление (deletedAt is null). */
public interface EngineersJpaRepository extends JpaRepository<EngineerJpaEntity, UUID> {
    Optional<EngineerJpaEntity> findByIdAndDeletedAtIsNull(UUID id);

    Optional<EngineerJpaEntity> findFirstByUsernameAndDeletedAtIsNull(String username);

    Optional<EngineerJpaEntity> findFirstByKeycloakUserIdAndDeletedAtIsNull(String keycloakUserId);

    boolean existsByServiceDepartmentIdAndDeletedAtIsNull(UUID serviceDepartmentId);
}
