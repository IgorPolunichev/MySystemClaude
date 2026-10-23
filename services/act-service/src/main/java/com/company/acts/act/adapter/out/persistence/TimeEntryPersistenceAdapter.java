package com.company.acts.act.adapter.out.persistence;

import com.company.acts.act.application.port.out.TimeEntryRepository;
import com.company.acts.act.domain.ActStatus;
import com.company.acts.act.domain.TimeEntry;
import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
class TimeEntryPersistenceAdapter implements TimeEntryRepository {

    private final TimeEntryJpaRepository repository;
    private final EntityManager em;

    TimeEntryPersistenceAdapter(TimeEntryJpaRepository repository, EntityManager em) {
        this.repository = repository;
        this.em = em;
    }

    @Override
    public List<TimeEntry> findByAct(UUID actId) {
        return repository.findByActIdOrderByWorkDateAsc(actId).stream().map(TimeEntryPersistenceAdapter::toDomain)
                .toList();
    }

    @Override
    public Optional<TimeEntry> find(UUID actId, UUID engineerId, LocalDate date) {
        return repository.findByActIdAndEngineerIdAndWorkDate(actId, engineerId, date)
                .map(TimeEntryPersistenceAdapter::toDomain);
    }

    @Override
    public TimeEntry save(TimeEntry t) {
        TimeEntryJpaEntity e = repository.findById(t.id()).orElseGet(() -> {
            TimeEntryJpaEntity created = new TimeEntryJpaEntity();
            created.setId(t.id());
            created.setActId(t.actId());
            created.setEngineerId(t.engineerId());
            created.setWorkDate(t.workDate());
            return created;
        });
        e.setHours(t.hours());
        e.setTravelHours(t.travelHours());
        e.setComment(t.comment());
        return toDomain(repository.saveAndFlush(e));
    }

    @Override
    public void delete(UUID entryId) {
        repository.findById(entryId).ifPresent(e -> {
            repository.delete(e);
            repository.flush();
        });
    }

    @Override
    public boolean existsByAct(UUID actId) {
        return repository.existsByActId(actId);
    }

    @Override
    public boolean existsByActAndEngineer(UUID actId, UUID engineerId) {
        return repository.existsByActIdAndEngineerId(actId, engineerId);
    }

    /** Аннулированные акты не учитываются. */
    @Override
    public BigDecimal hoursOfEngineerOnDate(UUID engineerId, LocalDate date, UUID excludeEntryId) {
        BigDecimal total = em.createQuery("""
                        select coalesce(sum(t.hours), 0) from TimeEntryJpaEntity t, WorkActJpaEntity a
                        where a.id = t.actId and t.engineerId = :engineerId and t.workDate = :date
                          and t.id <> :exclude and a.status <> :cancelled
                        """, BigDecimal.class)
                .setParameter("engineerId", engineerId)
                .setParameter("date", date)
                .setParameter("exclude", excludeEntryId)
                .setParameter("cancelled", ActStatus.CANCELLED)
                .getSingleResult();
        return total == null ? BigDecimal.ZERO : total;
    }

    private static TimeEntry toDomain(TimeEntryJpaEntity e) {
        return new TimeEntry(e.getId(), e.getActId(), e.getEngineerId(), e.getWorkDate(), e.getHours(),
                e.getTravelHours(), e.getComment());
    }
}
