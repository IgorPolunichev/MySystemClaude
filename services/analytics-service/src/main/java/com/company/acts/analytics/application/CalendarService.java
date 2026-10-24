package com.company.acts.analytics.application;

import com.company.acts.analytics.application.port.out.CalendarStore;
import com.company.acts.analytics.domain.WorkCalendar;
import com.company.acts.common.core.Strings;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Производственный календарь: исключения из правила «пн–пт рабочие». Изменяет администратор. */
@Service
public class CalendarService {

    public record Day(LocalDate date, boolean working) {
    }

    public record MonthSummary(YearMonth month, int workingDays) {
    }

    public record YearView(int year, List<Day> exceptions, List<MonthSummary> months) {
    }

    private final CalendarStore store;

    public CalendarService(CalendarStore store) {
        this.store = store;
    }

    @Transactional(readOnly = true)
    public YearView year(int year) {
        LocalDate from = LocalDate.of(year, 1, 1);
        LocalDate to = LocalDate.of(year, 12, 31);
        Map<LocalDate, Boolean> overrides = store.overrides(from, to);
        WorkCalendar calendar = new WorkCalendar(overrides);
        List<MonthSummary> months = new ArrayList<>();
        for (int m = 1; m <= 12; m++) {
            YearMonth ym = YearMonth.of(year, m);
            months.add(new MonthSummary(ym, calendar.workingDays(ym.atDay(1), ym.atEndOfMonth())));
        }
        List<Day> exceptions = overrides.entrySet().stream().sorted(Map.Entry.comparingByKey())
                .map(e -> new Day(e.getKey(), e.getValue())).toList();
        return new YearView(year, exceptions, months);
    }

    @PreAuthorize("hasRole('ADMIN')")
    @Transactional
    public void set(LocalDate day, Boolean working, String comment) {
        store.set(day, working, Strings.trimToNull(comment));
    }
}
