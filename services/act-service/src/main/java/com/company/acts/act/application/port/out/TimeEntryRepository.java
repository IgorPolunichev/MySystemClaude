package com.company.acts.act.application.port.out;

import com.company.acts.act.domain.TimeEntry;
import java.math.BigDecimal;
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

    /** Продуктивные часы инженера за день по всем незакрытым и закрытым актам, кроме указанной записи. */
    BigDecimal hoursOfEngineerOnDate(UUID engineerId, LocalDate date, UUID excludeEntryId);
}
