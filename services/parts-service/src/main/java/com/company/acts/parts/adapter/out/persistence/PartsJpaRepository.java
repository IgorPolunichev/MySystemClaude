package com.company.acts.parts.adapter.out.persistence;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PartsJpaRepository extends JpaRepository<PartJpaEntity, UUID> {

    Optional<PartJpaEntity> findByIdAndDeletedAtIsNull(UUID id);

    Optional<PartJpaEntity> findFirstByNameIgnoreCaseAndDeletedAtIsNull(String name);
}
