package com.company.acts.act.adapter.out.persistence;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PartLineJpaRepository extends JpaRepository<PartLineJpaEntity, UUID> {

    List<PartLineJpaEntity> findByActIdOrderByPositionAsc(UUID actId);
}
