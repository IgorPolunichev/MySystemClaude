package com.company.acts.directory.adapter.out.persistence;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ExecutorJpaRepository extends JpaRepository<ExecutorJpaEntity, UUID> {

    Optional<ExecutorJpaEntity> findFirstByIsDefaultTrueAndDeletedAtIsNull();

    Optional<ExecutorJpaEntity> findFirstByInnAndDeletedAtIsNull(String inn);

    List<ExecutorJpaEntity> findByDeletedAtIsNullOrderByNameAsc();
}
