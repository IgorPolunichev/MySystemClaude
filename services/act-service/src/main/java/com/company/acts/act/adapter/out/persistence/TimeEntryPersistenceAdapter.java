package com.company.acts.act.adapter.out.persistence;

import com.company.acts.act.application.port.out.TimeEntryRepository;
import com.company.acts.act.domain.ActStatus;
import com.company.acts.act.domain.TimeEntry;
import com.company.acts.act.domain.WorkBreak;
import com.company.acts.common.core.Uuid7;
import jakarta.persistence.EntityManager;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

/** Записи табеля и их перерывы. Перерывы записи перезаписываются целиком при каждом сохранении. */
@Component
class TimeEntryPersistenceAdapter implements TimeEntryRepository {

    private final TimeEntryJpaRepository repository;
    private final TimeEntryBreakJpaRepository breaks;
    private final EntityManager em;

    TimeEntryPersistenceAdapter(TimeEntryJpaRepository repository, TimeEntryBreakJpaRepository breaks,
                                EntityManager em) {
        this.repository = repository;
        this.breaks = breaks;
        this.em = em;
    }

    @Override
    public List<TimeEntry> findByAct(UUID actId) {
        List<TimeEntryJpaEntity> entries = repository.findByActIdOrderByWorkDateAsc(actId);
        if (entries.isEmpty()) {
            return List.of();
        }
        Map<UUID, List<TimeEntryBreakJpaEntity>> byEntry = breaks
                .findByEntryIdInOrderByPositionAsc(entries.stream().map(TimeEntryJpaEntity::getId).toList())
                .stream().collect(Collectors.groupingBy(TimeEntryBreakJpaEntity::getEntryId));
        return entries.stream().map(e -> toDomain(e, byEntry.getOrDefault(e.getId(), List.of()))).toList();
    }

    @Override
    public Optional<TimeEntry> find(UUID actId, UUID engineerId, LocalDate date) {
        return repository.findByActIdAndEngineerIdAndWorkDate(actId, engineerId, date)
                .map(e -> toDomain(e, breaks.findByEntryId(e.getId())));
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
        e.setStartTime(t.startTime());
        e.setEndTime(t.endTime());
        e.setWorkMinutes(t.workMinutes());
        e.setHours(t.hours());
        e.setTravelHours(t.travelHours());
        e.setComment(t.comment());
        repository.saveAndFlush(e);

        breaks.deleteAll(breaks.findByEntryId(t.id()));
        breaks.flush();
        int position = 1;
        for (WorkBreak b : t.breaks()) {
            TimeEntryBreakJpaEntity row = new TimeEntryBreakJpaEntity();
            row.setId(Uuid7.next());
            row.setEntryId(t.id());
            row.setPosition(position++);
            row.setStartTime(b.start());
            row.setEndTime(b.end());
            breaks.save(row);
        }
        breaks.flush();
        return t;
    }

    @Override
    public void delete(UUID entryId) {
        repository.findById(entryId).ifPresent(e -> {
            breaks.deleteAll(breaks.findByEntryId(entryId));
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
    public long minutesOfEngineerOnDate(UUID engineerId, LocalDate date, UUID excludeEntryId) {
        Long total = em.createQuery("""
                        select sum(t.workMinutes) from TimeEntryJpaEntity t, WorkActJpaEntity a
                        where a.id = t.actId and t.engineerId = :engineerId and t.workDate = :date
                          and t.id <> :exclude and a.status <> :cancelled
                        """, Long.class)
                .setParameter("engineerId", engineerId)
                .setParameter("date", date)
                .setParameter("exclude", excludeEntryId)
                .setParameter("cancelled", ActStatus.CANCELLED)
                .getSingleResult();
        return total == null ? 0 : total;
    }

    private static TimeEntry toDomain(TimeEntryJpaEntity e, List<TimeEntryBreakJpaEntity> rows) {
        List<WorkBreak> list = rows.stream().map(b -> new WorkBreak(b.getStartTime(), b.getEndTime())).toList();
        return new TimeEntry(e.getId(), e.getActId(), e.getEngineerId(), e.getWorkDate(), e.getStartTime(),
                e.getEndTime(), list, e.getTravelHours(), e.getComment());
    }
}
