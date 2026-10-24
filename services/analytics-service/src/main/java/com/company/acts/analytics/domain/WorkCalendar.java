package com.company.acts.analytics.domain;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.Map;

/**
 * Производственный календарь: по умолчанию рабочие дни — понедельник–пятница; праздники, переносы и рабочие
 * субботы задаёт администратор как исключения (раздел 9 SPEC.md).
 */
public final class WorkCalendar {

    private final Map<LocalDate, Boolean> overrides;

    public WorkCalendar(Map<LocalDate, Boolean> overrides) {
        this.overrides = Map.copyOf(overrides);
    }

    public boolean isWorking(LocalDate day) {
        Boolean override = overrides.get(day);
        if (override != null) {
            return override;
        }
        DayOfWeek dow = day.getDayOfWeek();
        return dow != DayOfWeek.SATURDAY && dow != DayOfWeek.SUNDAY;
    }

    /** Рабочих дней в интервале [from; to] включительно. */
    public int workingDays(LocalDate from, LocalDate to) {
        int count = 0;
        for (LocalDate d = from; !d.isAfter(to); d = d.plusDays(1)) {
            if (isWorking(d)) {
                count++;
            }
        }
        return count;
    }
}
