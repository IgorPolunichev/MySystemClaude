package com.company.acts.media.adapter.out.persistence;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MediaJpaRepository extends JpaRepository<MediaJpaEntity, UUID> {

    Optional<MediaJpaEntity> findByIdAndDeletedAtIsNull(UUID id);
}
