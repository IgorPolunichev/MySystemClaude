package com.company.acts.act.adapter.out.persistence;

import com.company.acts.act.application.port.out.ActHistoryRepository;
import com.company.acts.act.application.view.HistoryEntry;
import com.company.acts.common.core.Uuid7;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
class HistoryPersistenceAdapter implements ActHistoryRepository {

    private final HistoryJpaRepository repository;

    HistoryPersistenceAdapter(HistoryJpaRepository repository) {
        this.repository = repository;
    }

    @Override
    public void append(UUID actId, String action, String details, String user) {
        HistoryJpaEntity e = new HistoryJpaEntity();
        e.setId(Uuid7.next());
        e.setActId(actId);
        e.setChangedAt(Instant.now());
        e.setChangedBy(user);
        e.setAction(action);
        e.setDetails(details == null || details.length() <= 1000 ? details : details.substring(0, 1000));
        repository.saveAndFlush(e);
    }

    @Override
    public List<HistoryEntry> findByAct(UUID actId) {
        return repository.findByActIdOrderByChangedAtAsc(actId).stream()
                .map(h -> new HistoryEntry(h.getChangedAt(), h.getChangedBy(), h.getAction(), h.getDetails()))
                .toList();
    }
}
