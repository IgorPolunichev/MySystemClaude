package com.company.acts.act.adapter.out.persistence;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface HistoryJpaRepository extends JpaRepository<HistoryJpaEntity, UUID> {

    List<HistoryJpaEntity> findByActIdOrderByChangedAtAsc(UUID actId);
}
