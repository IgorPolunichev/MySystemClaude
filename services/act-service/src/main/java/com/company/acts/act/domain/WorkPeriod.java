package com.company.acts.act.domain;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.Collection;
import java.util.Comparator;

/**
 * Период работ: по умолчанию — от первой до последней даты записей времени; может быть задан вручную.
 *
 * @param start        дата начала или {@code null}, если записей нет
 * @param end          дата окончания или {@code null}
 * @param calendarDays календарных дней от начала до конца включительно
 * @param workedDays   дней, в которые было записано время
 */
public record WorkPeriod(LocalDate start, LocalDate end, long calendarDays, long workedDays) {

    public static WorkPeriod of(WorkAct act, Collection<TimeEntry> entries) {
        LocalDate first = entries.stream().map(TimeEntry::workDate).min(Comparator.naturalOrder()).orElse(null);
        LocalDate last = entries.stream().map(TimeEntry::workDate).max(Comparator.naturalOrder()).orElse(null);
        LocalDate start = act.workStartOverride() != null ? act.workStartOverride() : first;
        LocalDate end = act.workEndOverride() != null ? act.workEndOverride() : last;
        long calendar = start != null && end != null && !end.isBefore(start)
                ? ChronoUnit.DAYS.between(start, end) + 1 : 0;
        long worked = entries.stream().map(TimeEntry::workDate).distinct().count();
        return new WorkPeriod(start, end, calendar, worked);
    }
}
