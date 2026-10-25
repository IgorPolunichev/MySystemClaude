package com.company.acts.analytics;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.company.acts.analytics.application.DashboardService;
import com.company.acts.analytics.application.EventService;
import com.company.acts.analytics.application.NormService;
import com.company.acts.analytics.application.RebuildService;
import com.company.acts.analytics.application.port.out.ActFactsClient;
import com.company.acts.analytics.application.port.out.DirectoryClient;
import com.company.acts.analytics.application.port.out.FactsStore;
import com.company.acts.analytics.application.view.DashboardView;
import com.company.acts.common.test.PostgresIntegrationTest;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

/** Аналитика на реальном PostgreSQL: события → read model → дашборд; нормы; права; пересборка. */
@SpringBootTest(properties = "spring.rabbitmq.listener.simple.auto-startup=false")
class AnalyticsScenariosIT extends PostgresIntegrationTest {

    static final UUID D1 = UUID.randomUUID();
    static final UUID D2 = UUID.randomUUID();
    static final UUID E1 = UUID.randomUUID();
    static final UUID E2 = UUID.randomUUID();
    static final UUID E3 = UUID.randomUUID();
    static final UUID ORG = UUID.randomUUID();

    @TestConfiguration
    static class Fakes {
        @Bean
        @Primary
        DirectoryClient fakeDirectory() {
            return new DirectoryClient() {
                final List<EngineerRef> all = List.of(
                        new EngineerRef(E1, "kc-e1", "Петров И. С.", D1, "Отдел 1", true),
                        new EngineerRef(E2, "kc-e2", "Смирнов А. В.", D1, "Отдел 1", true),
                        new EngineerRef(E3, "kc-e3", "Кузнецов С. А.", D2, "Отдел 2", true));

                public List<EngineerRef> engineers() {
                    return all;
                }

                public List<DepartmentRef> departments() {
                    return List.of(new DepartmentRef(D1, "Отдел 1"), new DepartmentRef(D2, "Отдел 2"));
                }

                public EngineerRef me() {
                    String name = SecurityContextHolder.getContext().getAuthentication().getName();
                    return all.stream().filter(e -> e.keycloakUserId().equals(name)).findFirst().orElse(null);
                }
            };
        }

        @Bean
        @Primary
        ActFactsClient fakeFacts() {
            UUID act = UUID.fromString("00000000-0000-4000-8000-0000000000aa");
            return () -> new ActFactsClient.Facts(
                    List.of(new FactsStore.ActFact(act, "АВР-2026-000099", "CLOSED", ORG, UUID.randomUUID(), null)),
                    List.of(new FactsStore.TimeFact(act, E3, LocalDate.of(2027, 2, 1), 300)));
        }
    }

    @Autowired
    EventService events;
    @Autowired
    DashboardService dashboard;
    @Autowired
    NormService norms;
    @Autowired
    RebuildService rebuild;
    @Autowired
    com.company.acts.analytics.application.ScheduleService schedules;

    @AfterEach
    void logout() {
        SecurityContextHolder.clearContext();
    }

    static void login(String user, String... roles) {
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(user, "n/a",
                Arrays.stream(roles).map(r -> new SimpleGrantedAuthority("ROLE_" + r)).toList()));
    }

    private void event(String type, UUID actId, Map<String, Object> payload) {
        Map<String, Object> envelope = new LinkedHashMap<>();
        envelope.put("eventId", UUID.randomUUID().toString());
        envelope.put("eventType", type);
        envelope.put("aggregateId", actId.toString());
        envelope.put("payload", payload);
        events.apply(envelope);
    }

    private static Map<String, Object> act(String status) {
        Map<String, Object> p = new LinkedHashMap<>();
        p.put("status", status);
        p.put("number", "АВР-2026-000001");
        p.put("organizationId", ORG.toString());
        p.put("equipmentId", UUID.randomUUID().toString());
        return p;
    }

    private static Map<String, Object> time(UUID engineer, String date, int minutes) {
        return Map.of("engineerId", engineer.toString(), "workDate", date, "workMinutes", String.valueOf(minutes));
    }

    @Test
    void eventsBuildFactsAndDashboardShowsNormsAndPercent() {
        login("admin", "ADMIN");
        UUID open = UUID.randomUUID();
        UUID draft = UUID.randomUUID();
        UUID cancelled = UUID.randomUUID();
        event("ActCreated", open, act("OPEN"));
        event("TimeEntryChanged", open, time(E1, "2026-10-05", 480));
        event("TimeEntryChanged", open, time(E1, "2026-10-06", 240));
        event("TimeEntryChanged", open, time(E2, "2026-10-06", 300));
        event("ActCreated", draft, act("DRAFT"));
        event("TimeEntryChanged", draft, time(E1, "2026-10-07", 600));
        event("ActCreated", cancelled, act("OPEN"));
        event("TimeEntryChanged", cancelled, time(E1, "2026-10-08", 600));
        event("ActCancelled", cancelled, act("CANCELLED"));

        // повтор того же события не учитывается дважды
        Map<String, Object> duplicate = new LinkedHashMap<>(Map.of("eventId", UUID.randomUUID().toString(),
                "eventType", "TimeEntryChanged", "aggregateId", open.toString(), "payload", time(E3, "2026-10-09", 120)));
        events.apply(duplicate);
        events.apply(duplicate);

        norms.setEngineerNorm(E1, YearMonth.of(2026, 10), new BigDecimal("168"));
        norms.setEngineerNorm(E2, YearMonth.of(2026, 10), new BigDecimal("160"));

        DashboardView view = dashboard.dashboard(LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 31), null, null, null);
        assertThat(view.scope()).isEqualTo(DashboardView.Scope.ALL);
        DashboardView.EngineerRow e1 = row(view, E1);
        assertThat(e1.factHours()).isEqualByComparingTo("12");
        assertThat(e1.normHours()).isEqualByComparingTo("168");
        assertThat(e1.percent()).isEqualTo(7);
        assertThat(e1.acts()).isEqualTo(1);
        assertThat(row(view, E3).factHours()).isEqualByComparingTo("2");
        DashboardView.DepartmentRow d1 = view.departments().stream().filter(d -> d.departmentId().equals(D1))
                .findFirst().orElseThrow();
        assertThat(d1.normComputed()).isTrue();
        assertThat(d1.normHours()).isEqualByComparingTo("328");
        assertThat(d1.factHours()).isEqualByComparingTo("17");

        norms.setDepartmentNorm(D1, YearMonth.of(2026, 10), new BigDecimal("300"));
        DashboardView.DepartmentRow explicit = dashboard.dashboard(LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 31),
                null, null, null).departments().stream().filter(d -> d.departmentId().equals(D1)).findFirst().orElseThrow();
        assertThat(explicit.normComputed()).isFalse();
        assertThat(explicit.normHours()).isEqualByComparingTo("300");

        assertThat(dashboard.series(E1, LocalDate.of(2026, 10, 5), LocalDate.of(2026, 10, 18), true))
                .extracting(DashboardService.Point::hours).map(BigDecimal::stripTrailingZeros)
                .containsExactly(new BigDecimal("12"), BigDecimal.ZERO);

        assertThat(norms.copyFromPrevious(YearMonth.of(2026, 11))).isGreaterThanOrEqualTo(3);
        assertThat(norms.month(YearMonth.of(2026, 11)).engineers()).anySatisfy(n -> {
            assertThat(n.engineerId()).isEqualTo(E1);
            assertThat(n.hours()).isEqualByComparingTo("168");
        });

        event("ActDeleted", open, act("OPEN"));
        assertThat(row(dashboard.dashboard(LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 31), null, null, null), E1)
                .factHours()).isEqualByComparingTo("0");
    }

    @Test
    void engineerSeesOnlySelfAndHeadOnlyOwnDepartment() {
        login("kc-e1", "ENGINEER");
        DashboardView mine = dashboard.dashboard(LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 31), null, null, null);
        assertThat(mine.scope()).isEqualTo(DashboardView.Scope.SELF);
        assertThat(mine.engineers()).extracting(DashboardView.EngineerRow::engineerId).containsExactly(E1);
        assertThatThrownBy(() -> dashboard.series(E2, LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 31), false))
                .isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> norms.setEngineerNorm(E1, YearMonth.of(2026, 1), BigDecimal.TEN))
                .isInstanceOf(AccessDeniedException.class);

        login("kc-e2", "DEPARTMENT_HEAD");
        DashboardView department = dashboard.dashboard(LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 31), null, null,
                null);
        assertThat(department.scope()).isEqualTo(DashboardView.Scope.DEPARTMENT);
        assertThat(department.engineers()).extracting(DashboardView.EngineerRow::engineerId)
                .containsExactlyInAnyOrder(E1, E2);
        assertThat(norms.setForAllEngineers(D1, YearMonth.of(2026, 1), new BigDecimal("150"))).isEqualTo(2);
        assertThatThrownBy(() -> norms.setDepartmentNorm(D2, YearMonth.of(2026, 1), BigDecimal.TEN))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void rebuildReplacesFactsFromActService() {
        login("admin", "ADMIN");
        RebuildService.Result result = rebuild.rebuild();
        assertThat(result.acts()).isEqualTo(1);
        assertThat(row(dashboard.dashboard(LocalDate.of(2027, 2, 1), LocalDate.of(2027, 2, 28), null, null, null), E3)
                .factHours()).isEqualByComparingTo("5");
        login("kc-e1", "ENGINEER");
        assertThatThrownBy(() -> rebuild.rebuild()).isInstanceOf(AccessDeniedException.class);
    }

    private static DashboardView.EngineerRow row(DashboardView view, UUID id) {
        return view.engineers().stream().filter(r -> r.engineerId().equals(id)).findFirst().orElseThrow();
    }

    @Test
    void schedulesAreBuiltShiftedMarkedAndProtected() {
        var command = new com.company.acts.analytics.application.ScheduleService.TemplateCommand("2/2 днём",
                com.company.acts.analytics.domain.schedule.ScheduleMode.ROTATION, 2, 2, java.time.LocalTime.of(8, 0),
                java.time.LocalTime.of(20, 0), 60, null, null, null, 0, false, 0, true);
        login("kc-e1", "ENGINEER");
        assertThatThrownBy(() -> schedules.saveTemplate(null, command)).isInstanceOf(AccessDeniedException.class);

        login("kc-e2", "DEPARTMENT_HEAD");
        var template = schedules.saveTemplate(null, command);
        assertThat(template.dayHours()).isEqualByComparingTo("11");
        var group = schedules.createGroup(new com.company.acts.analytics.application.ScheduleService.GroupCommand(null,
                "Отдел 1 — 2/2", D1, null, "Северный карьер", template.id(), LocalDate.of(2026, 1, 1), null, null,
                List.of(E1, E2)));
        assertThat(group.members()).extracting(m -> m.crew().name()).containsExactly("A", "B");
        assertThat(group.members().get(0).partnerId()).isEqualTo(E2);
        assertThat(group.requiredDay()).isEqualTo(1);
        // руководитель не составляет графики чужого отдела
        assertThatThrownBy(() -> schedules.createGroup(new com.company.acts.analytics.application.ScheduleService.GroupCommand(
                null, "Чужой", D2, null, null, template.id(), LocalDate.of(2026, 1, 1), null, null, List.of(E3))))
                .isInstanceOf(AccessDeniedException.class);

        var grid = schedules.grid(group.id(), LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 8));
        assertThat(grid.employees().get(0).days()).extracting(d -> d.letter()).containsExactly("Д", "Д", "В", "В", "Д", "Д", "В", "В");
        assertThat(grid.employees().get(1).days()).extracting(d -> d.letter()).containsExactly("В", "В", "Д", "Д", "В", "В", "Д", "Д");
        assertThat(grid.employees().get(0).totalHours()).isEqualByComparingTo("44");
        assertThat(grid.shortageDays()).isZero();

        // смещение заезда одного — сменщик сдвигается вместе с ним; покрытие сохраняется
        var shifted = schedules.shiftArrival(group.id(), java.util.Set.of(E1), 1);
        assertThat(shifted.members()).extracting(m -> m.offsetDays()).containsExactly(1, 1);
        assertThat(schedules.grid(group.id(), LocalDate.of(2026, 1, 1), LocalDate.of(2026, 3, 31)).shortageDays()).isZero();

        // отпуск без замены — нехватка; утверждение снимается любой правкой
        schedules.approve(group.id());
        schedules.mark(group.id(), E1, LocalDate.of(2026, 1, 2), LocalDate.of(2026, 1, 3),
                com.company.acts.analytics.domain.schedule.ShiftKind.VACATION, "Отпуск");
        var marked = schedules.grid(group.id(), LocalDate.of(2026, 1, 2), LocalDate.of(2026, 1, 3));
        assertThat(marked.employees().get(0).days()).allSatisfy(d -> assertThat(d.manual()).isTrue());
        assertThat(marked.shortageDays()).isEqualTo(2);
        assertThat(schedules.group(group.id()).approvedAt()).isNull();

        // норма по графику для дашборда: смена 11 ч в рабочий день
        assertThat(schedules.plannedMinutes(E2, LocalDate.of(2026, 1, 4), LocalDate.of(2026, 1, 4)))
                .containsEntry(LocalDate.of(2026, 1, 4), 660);
    }
}
