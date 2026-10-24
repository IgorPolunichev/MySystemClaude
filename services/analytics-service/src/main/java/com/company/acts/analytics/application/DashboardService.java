package com.company.acts.analytics.application;

import com.company.acts.analytics.application.port.out.CalendarStore;
import com.company.acts.analytics.application.port.out.DirectoryClient;
import com.company.acts.analytics.application.port.out.FactsStore;
import com.company.acts.analytics.application.port.out.NormStore;
import com.company.acts.analytics.application.view.DashboardView;
import com.company.acts.analytics.domain.NormCalculator;
import com.company.acts.analytics.domain.WorkCalendar;
import com.company.acts.common.core.BusinessRuleException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Дашборд продуктивных часов (раздел 9 SPEC.md): факт по табелю (акты OPEN и CLOSED), норма на период
 * (пропорционально рабочим дням для неполных месяцев), процент выполнения, графики по дням и неделям.
 */
@Service
public class DashboardService {

    public record Point(LocalDate start, BigDecimal hours) {
    }

    private static final int MAX_DAYS = 3 * 366;

    private final FactsStore facts;
    private final NormStore norms;
    private final CalendarStore calendar;
    private final DirectoryClient directory;
    private final AccessScope access;
    private final DashboardView.Thresholds thresholds;

    public DashboardService(FactsStore facts, NormStore norms, CalendarStore calendar, DirectoryClient directory,
                            AccessScope access, @Value("${app.dashboard.threshold-red:70}") int red,
                            @Value("${app.dashboard.threshold-yellow:90}") int yellow) {
        this.facts = facts;
        this.norms = norms;
        this.calendar = calendar;
        this.directory = directory;
        this.access = access;
        this.thresholds = new DashboardView.Thresholds(red, yellow);
    }

    @Transactional(readOnly = true)
    public DashboardView dashboard(LocalDate from, LocalDate to, UUID departmentId, Collection<UUID> engineerIds,
                                   UUID organizationId) {
        validate(from, to);
        AccessScope.Visible visible = access.resolve();
        List<DirectoryClient.EngineerRef> engineers = visible.engineers().stream()
                .filter(e -> departmentId == null || departmentId.equals(e.departmentId()))
                .filter(e -> engineerIds == null || engineerIds.isEmpty() || engineerIds.contains(e.id()))
                .toList();
        Set<UUID> ids = engineers.stream().map(DirectoryClient.EngineerRef::id).collect(Collectors.toSet());
        Map<UUID, FactsStore.EngineerTotal> totals = ids.isEmpty() ? Map.of()
                : facts.engineerTotals(from, to, organizationId, ids).stream()
                .collect(Collectors.toMap(FactsStore.EngineerTotal::engineerId, Function.identity()));
        WorkCalendar cal = new WorkCalendar(calendar.overrides(YearMonth.from(from).atDay(1),
                YearMonth.from(to).atEndOfMonth()));
        Map<UUID, Map<YearMonth, BigDecimal>> engineerNorms = norms.engineerNorms(YearMonth.from(from), YearMonth.from(to));

        List<DashboardView.EngineerRow> rows = new ArrayList<>();
        for (DirectoryClient.EngineerRef e : engineers) {
            FactsStore.EngineerTotal t = totals.get(e.id());
            BigDecimal fact = hours(t == null ? 0 : t.minutes());
            BigDecimal norm = NormCalculator.forPeriod(engineerNorms.getOrDefault(e.id(), Map.of()), from, to, cal);
            if (!e.active() && t == null && norm == null) {
                continue;
            }
            rows.add(new DashboardView.EngineerRow(e.id(), e.fullName(), e.departmentId(), e.departmentName(), norm,
                    fact, NormCalculator.percent(fact, norm), t == null ? 0 : t.acts(), t == null ? 0 : t.machines()));
        }
        rows.sort((a, b) -> a.fullName().compareToIgnoreCase(b.fullName()));

        Map<UUID, Map<YearMonth, BigDecimal>> departmentNorms = norms.departmentNorms(YearMonth.from(from),
                YearMonth.from(to));
        Map<UUID, String> departmentNames = directory.departments().stream()
                .collect(Collectors.toMap(DirectoryClient.DepartmentRef::id, DirectoryClient.DepartmentRef::name,
                        (x, y) -> x, LinkedHashMap::new));
        List<DashboardView.DepartmentRow> departments = new ArrayList<>();
        Map<UUID, List<DashboardView.EngineerRow>> byDepartment = rows.stream().filter(r -> r.departmentId() != null)
                .collect(Collectors.groupingBy(DashboardView.EngineerRow::departmentId));
        for (Map.Entry<UUID, List<DashboardView.EngineerRow>> d : byDepartment.entrySet()) {
            BigDecimal explicit = NormCalculator.forPeriod(departmentNorms.getOrDefault(d.getKey(), Map.of()), from, to,
                    cal);
            BigDecimal computed = d.getValue().stream().map(DashboardView.EngineerRow::normHours)
                    .filter(java.util.Objects::nonNull).reduce(null, (x, y) -> x == null ? y : x.add(y));
            BigDecimal norm = explicit != null ? explicit : computed;
            BigDecimal fact = d.getValue().stream().map(DashboardView.EngineerRow::factHours)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            departments.add(new DashboardView.DepartmentRow(d.getKey(),
                    departmentNames.getOrDefault(d.getKey(), "Отдел"), norm, explicit == null && computed != null, fact,
                    NormCalculator.percent(fact, norm)));
        }
        departments.sort((a, b) -> a.name().compareToIgnoreCase(b.name()));
        return new DashboardView(from, to, rows, departments, thresholds, visible.scope());
    }

    /** Часы инженера по дням или неделям (с понедельника) за период. */
    @Transactional(readOnly = true)
    public List<Point> series(UUID engineerId, LocalDate from, LocalDate to, boolean weekly) {
        validate(from, to);
        if (!access.resolve().contains(engineerId)) {
            throw new AccessDeniedException("Нет доступа к показателям этого инженера");
        }
        Map<LocalDate, Long> buckets = new TreeMap<>();
        LocalDate cursor = weekly ? from.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)) : from;
        while (!cursor.isAfter(to)) {
            buckets.put(cursor, 0L);
            cursor = cursor.plusDays(weekly ? 7 : 1);
        }
        for (FactsStore.DayMinutes d : facts.dailyMinutes(engineerId, from, to)) {
            LocalDate key = weekly ? d.day().with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)) : d.day();
            buckets.merge(key, d.minutes(), Long::sum);
        }
        return buckets.entrySet().stream().map(e -> new Point(e.getKey(), hours(e.getValue()))).toList();
    }

    private static void validate(LocalDate from, LocalDate to) {
        if (from == null || to == null || to.isBefore(from)) {
            throw new BusinessRuleException("from", "Укажите период: дата окончания не раньше даты начала");
        }
        if (from.plusDays(MAX_DAYS).isBefore(to)) {
            throw new BusinessRuleException("to", "Период — не больше трёх лет");
        }
    }

    static BigDecimal hours(long minutes) {
        return BigDecimal.valueOf(minutes).divide(BigDecimal.valueOf(60), 2, RoundingMode.HALF_UP);
    }
}
