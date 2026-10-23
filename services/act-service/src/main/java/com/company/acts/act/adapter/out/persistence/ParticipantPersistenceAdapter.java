package com.company.acts.act.adapter.out.persistence;

import com.company.acts.act.application.port.out.ParticipantRepository;
import com.company.acts.act.domain.Participant;
import com.company.acts.common.core.Uuid7;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
class ParticipantPersistenceAdapter implements ParticipantRepository {

    private final ParticipantJpaRepository repository;

    ParticipantPersistenceAdapter(ParticipantJpaRepository repository) {
        this.repository = repository;
    }

    @Override
    public List<Participant> findByAct(UUID actId) {
        return repository.findByActIdOrderByRoleAscFullNameAsc(actId).stream()
                .map(p -> new Participant(p.getEngineerId(), p.getKeycloakUserId(), p.getFullName(), p.getPosition(),
                        p.getRole()))
                .toList();
    }

    @Override
    public void save(UUID actId, Participant p) {
        ParticipantJpaEntity e = repository.findByActIdAndEngineerId(actId, p.engineerId()).orElseGet(() -> {
            ParticipantJpaEntity created = new ParticipantJpaEntity();
            created.setId(Uuid7.next());
            created.setActId(actId);
            created.setEngineerId(p.engineerId());
            created.setCreatedAt(Instant.now());
            return created;
        });
        e.setKeycloakUserId(p.keycloakUserId());
        e.setFullName(p.fullName());
        e.setPosition(p.position());
        e.setRole(p.role());
        repository.saveAndFlush(e);
    }

    @Override
    public void delete(UUID actId, UUID engineerId) {
        repository.findByActIdAndEngineerId(actId, engineerId).ifPresent(e -> {
            repository.delete(e);
            repository.flush();
        });
    }
}
