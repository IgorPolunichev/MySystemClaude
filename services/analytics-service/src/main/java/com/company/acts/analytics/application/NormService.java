package com.company.acts.analytics.application;

import com.company.acts.analytics.application.port.out.CalendarStore;
import com.company.acts.analytics.application.port.out.CurrentUserProvider;
import com.company.acts.analytics.application.port.out.DirectoryClient;
import com.company.acts.analytics.application.port.out.NormStore;
import com.company.acts.analytics.application.view.NormMonthView;
import com.company.acts.analytics.domain.WorkCalendar;
import com.company.acts.common.core.BusinessRuleException;
import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Нормы часов на месяц для инженеров и отделов (раздел 9 SPEC.md). Задают администратор (всем) и руководитель
 * отдела (своему отделу). Массовые операции: скопировать нормы прошлого месяца, задать норму всем инженерам отдела.
 */
@Service
public class NormService {

    private static final BigDecimal MAX_HOURS = BigDecimal.valueOf(744);

    private final NormStore norms;
    private final CalendarStore calendar;
    private final DirectoryClient directory;
    private final AccessScope access;
    private final CurrentUserProvider users;

    public NormService(NormStore norms, CalendarStore calendar, DirectoryClient directory, AccessScope access,
                       CurrentUserProvider users) {
        this.norms = norms;
        this.calendar = calendar;
        this.directory = directory;
        this.access = access;
        this.users = users;
    }

    @Transactional(readOnly = true)
    public NormMonthView month(YearMonth month) {
        AccessScope.Visible visible = access.resolve();
        Map<UUID, Map<YearMonth, BigDecimal>> engineerNorms = norms.engineerNorms(month, month);
        Map<UUID, Map<YearMonth, BigDecimal>> departmentNorms = norms.departmentNorms(month, month);
        List<NormMonthView.EngineerNorm> engineers = visible.engineers().stream()
                .filter(DirectoryClient.EngineerRef::active)
                .map(e -> new NormMonthView.EngineerNorm(e.id(), e.fullName(), e.departmentId(), e.departmentName(),
                        engineerNorms.getOrDefault(e.id(), Map.of()).get(month)))
                .sorted((a, b) -> a.fullName().compareToIgnoreCase(b.fullName()))
                .toList();
        List<NormMonthView.DepartmentNorm> departments = new ArrayList<>();
        for (DirectoryClient.DepartmentRef d : directory.departments()) {
            if (visible.scope() != com.company.acts.analytics.application.view.DashboardView.Scope.ALL
                    && !d.id().equals(visible.departmentId())) {
                continue;
            }
            BigDecimal computed = engineers.stream().filter(e -> d.id().equals(e.departmentId()))
                    .map(NormMonthView.EngineerNorm::hours).filter(Objects::nonNull)
                    .reduce(null, (x, y) -> x == null ? y : x.add(y));
            departments.add(new NormMonthView.DepartmentNorm(d.id(), d.name(),
                    departmentNorms.getOrDefault(d.id(), Map.of()).get(month), computed));
        }
        WorkCalendar cal = new WorkCalendar(calendar.overrides(month.atDay(1), month.atEndOfMonth()));
        return new NormMonthView(month, cal.workingDays(month.atDay(1), month.atEndOfMonth()), engineers, departments,
                visible.scope() != com.company.acts.analytics.application.view.DashboardView.Scope.SELF);
    }

    @Transactional
    public void setEngineerNorm(UUID engineerId, YearMonth month, BigDecimal hours) {
        AccessScope.Visible visible = access.resolve();
        DirectoryClient.EngineerRef engineer = visible.engineers().stream().filter(e -> e.id().equals(engineerId))
                .findFirst().orElseThrow(() -> new AccessDeniedException("Нет доступа к нормам этого инженера"));
        requireEdit(visible, engineer.departmentId());
        norms.setEngineerNorm(engineerId, month, validate(hours), users.current().username());
    }

    @Transactional
    public void setDepartmentNorm(UUID departmentId, YearMonth month, BigDecimal hours) {
        requireEdit(access.resolve(), departmentId);
        norms.setDepartmentNorm(departmentId, month, validate(hours), users.current().username());
    }

    /** «Установить норму всем инженерам отдела». */
    @Transactional
    public int setForAllEngineers(UUID departmentId, YearMonth month, BigDecimal hours) {
        AccessScope.Visible visible = access.resolve();
        requireEdit(visible, departmentId);
        BigDecimal value = validate(hours);
        int count = 0;
        for (DirectoryClient.EngineerRef e : visible.engineers()) {
            if (e.active() && departmentId.equals(e.departmentId())) {
                norms.setEngineerNorm(e.id(), month, value, users.current().username());
                count++;
            }
        }
        return count;
    }

    /** «Скопировать нормы прошлого месяца»: только администратор, уже заданные нормы не перезаписываются. */
    @Transactional
    public int copyFromPrevious(YearMonth month) {
        if (!users.current().isAdmin()) {
            throw new AccessDeniedException("Копировать нормы может администратор");
        }
        return norms.copyMonth(month.minusMonths(1), month, users.current().username());
    }

    private static void requireEdit(AccessScope.Visible visible, UUID departmentId) {
        if (!visible.canEditDepartment(departmentId)) {
            throw new AccessDeniedException("Нормы задают администратор и руководитель своего отдела");
        }
    }

    private static BigDecimal validate(BigDecimal hours) {
        if (hours != null && (hours.signum() < 0 || hours.compareTo(MAX_HOURS) > 0)) {
            throw new BusinessRuleException("hours", "Норма — от 0 до 744 часов в месяц");
        }
        return hours;
    }
}
