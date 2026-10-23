package com.company.acts.act.application.port.out;

import com.company.acts.act.domain.TimeEntry;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface TimeEntryRepository {

    List<TimeEntry> findByAct(UUID actId);

    Optional<TimeEntry> find(UUID actId, UUID engineerId, LocalDate date);

    TimeEntry save(TimeEntry entry);

    void delete(UUID entryId);

    boolean existsByAct(UUID actId);

    boolean existsByActAndEngineer(UUID actId, UUID engineerId);

    /** Продуктивные минуты инженера за день по всем неаннулированным актам, кроме указанной записи. */
    long minutesOfEngineerOnDate(UUID engineerId, LocalDate date, UUID excludeEntryId);
}
