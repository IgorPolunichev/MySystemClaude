package com.company.acts.act.adapter.out.persistence;

import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TimeEntryBreakJpaRepository extends JpaRepository<TimeEntryBreakJpaEntity, UUID> {

    List<TimeEntryBreakJpaEntity> findByEntryIdInOrderByPositionAsc(Collection<UUID> entryIds);

    List<TimeEntryBreakJpaEntity> findByEntryId(UUID entryId);
}
