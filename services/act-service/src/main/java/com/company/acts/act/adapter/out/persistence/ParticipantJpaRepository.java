package com.company.acts.act.adapter.out.persistence;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ParticipantJpaRepository extends JpaRepository<ParticipantJpaEntity, UUID> {

    List<ParticipantJpaEntity> findByActIdOrderByRoleAscFullNameAsc(UUID actId);

    Optional<ParticipantJpaEntity> findByActIdAndEngineerId(UUID actId, UUID engineerId);
}
