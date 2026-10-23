package com.company.acts.act.adapter.out.persistence;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TimeEntryJpaRepository extends JpaRepository<TimeEntryJpaEntity, UUID> {

    List<TimeEntryJpaEntity> findByActIdOrderByWorkDateAsc(UUID actId);

    Optional<TimeEntryJpaEntity> findByActIdAndEngineerIdAndWorkDate(UUID actId, UUID engineerId, LocalDate date);

    boolean existsByActId(UUID actId);

    boolean existsByActIdAndEngineerId(UUID actId, UUID engineerId);
}
