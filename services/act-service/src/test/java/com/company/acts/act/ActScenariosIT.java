package com.company.acts.act;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.company.acts.act.application.ActContentService;
import com.company.acts.act.application.ActService;
import com.company.acts.act.application.command.ActCreateCommand;
import com.company.acts.act.application.command.ActHeaderCommand;
import com.company.acts.act.application.command.PartLineCommand;
import com.company.acts.act.application.command.TimeEntryCommand;
import com.company.acts.act.application.port.out.DirectoryClient;
import com.company.acts.act.application.port.out.PartsClient;
import com.company.acts.act.application.view.ActDetail;
import com.company.acts.act.application.view.ActSearchQuery;
import com.company.acts.act.domain.ActStatus;
import com.company.acts.act.domain.ParticipantRole;
import com.company.acts.act.domain.PartSource;
import com.company.acts.common.core.BusinessRuleException;
import com.company.acts.common.core.ConflictException;
import com.company.acts.common.core.PageQuery;
import com.company.acts.common.test.PostgresIntegrationTest;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

/** Сценарии актов на реальном PostgreSQL: жизненный цикл, права, табель, запчасти, поиск, нумерация. */
@SpringBootTest
class ActScenariosIT extends PostgresIntegrationTest {

    @TestConfiguration
    static class FakeClients {
        @Bean
        @Primary
        FakeDirectory fakeDirectory() {
            return new FakeDirectory();
        }

        @Bean
        @Primary
        PartsClient fakeParts() {
            return (name, number, unit, fluid) -> UUID.nameUUIDFromBytes(("part:" + name).getBytes());
        }
    }

    @Autowired
    ActService acts;
    @Autowired
    ActContentService content;
    @Autowired
    FakeDirectory directory;
    @Autowired
    JdbcTemplate jdbc;

    private static final LocalDate DAY = LocalDate.of(2026, 10, 5);

    @AfterEach
    void logout() {
        SecurityContextHolder.clearContext();
    }

    static void login(String username, String... roles) {
        var authorities = Arrays.stream(roles.length == 0 ? new String[] {"ENGINEER"} : roles)
                .map(r -> new SimpleGrantedAuthority("ROLE_" + r)).toList();
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(username, "n/a", authorities));
    }

    /** Интервал работы «ЧЧ:ММ-ЧЧ:ММ» и перерывы в том же формате. */
    static TimeEntryCommand work(String interval, String... breaks) {
        String[] w = interval.split("-");
        var list = Arrays.stream(breaks).map(b -> b.split("-"))
                .map(b -> new TimeEntryCommand.BreakCommand(LocalTime.parse(b[0]), LocalTime.parse(b[1]))).toList();
        return new TimeEntryCommand(LocalTime.parse(w[0]), LocalTime.parse(w[1]), list, null, null);
    }

    static String user() {
        return "u" + UUID.randomUUID().toString().substring(0, 8);
    }

    private ActDetail header(ActDetail act, BigDecimal hours, String shortText, String adminComment) {
        return acts.updateHeader(act.id(), new ActHeaderCommand(act.version(), act.equipment().id(), hours, null,
                null, null, null, shortText, "Подробно", null, "Механик Сидоров", "Механик", null, null,
                adminComment));
    }

    @Test
    void lifecycleFromDraftToClosed() {
        String author = user();
        login(author);
        ActDetail draft = acts.create(new ActCreateCommand(null, UUID.randomUUID()));
        assertThat(draft.status()).isEqualTo(ActStatus.DRAFT);
        assertThat(draft.number()).isNull();
        assertThat(draft.hideRequisites()).isTrue();
        assertThat(draft.hideTravelTime()).isTrue();
        assertThat(draft.organization().shortName()).isEqualTo("АО «Тест»");
        assertThat(draft.engineers()).singleElement().satisfies(p -> {
            assertThat(p.role()).isEqualTo(ParticipantRole.LEAD);
            assertThat(p.isCurrentUser()).isTrue();
        });
        assertThat(draft.permissions().canOpen()).isTrue();

        ActDetail opened = acts.open(draft.id());
        assertThat(opened.number()).matches("АВР-\\d{4}-\\d{6}");
        ActDetail second = acts.open(acts.create(new ActCreateCommand(null, UUID.randomUUID())).id());
        long first = Long.parseLong(opened.number().substring(opened.number().length() - 6));
        assertThat(second.number()).endsWith("%06d".formatted(first + 1));

        UUID me = FakeDirectory.engineerId(author);
        // Пример из ТЗ: 10:00–13:00, обед 11:00–12:00 → 2 ч продуктивного времени; плюс 1,5 ч в пути
        content.putTime(opened.id(), me, DAY, new TimeEntryCommand(LocalTime.of(10, 0), LocalTime.of(13, 0),
                List.of(new TimeEntryCommand.BreakCommand(LocalTime.of(11, 0), LocalTime.of(12, 0))),
                new BigDecimal("1.5"), null));
        // 08:00–17:00, обед 12:00–13:00, перерыв 15:00–15:30 → 7,5 ч
        content.putTime(opened.id(), me, DAY.plusDays(2), work("08:00-17:00", "12:00-13:00", "15:00-15:30"));
        ActDetail withTime = acts.get(opened.id());
        assertThat(withTime.timeEntries().get(0).workMinutes()).isEqualTo(120);
        assertThat(withTime.timeEntries().get(0).hours()).isEqualByComparingTo("2");
        assertThat(withTime.timeEntries().get(0).breaks()).hasSize(1);
        assertThat(withTime.timeEntries().get(1).hours()).isEqualByComparingTo("7.5");
        assertThat(withTime.totalHours()).isEqualByComparingTo("9.5");
        assertThat(withTime.totalTravelHours()).isEqualByComparingTo("1.5");
        assertThat(withTime.workStartDate()).isEqualTo(DAY);
        assertThat(withTime.calendarDays()).isEqualTo(3);
        assertThat(withTime.workedDays()).isEqualTo(2);
        assertThat(withTime.missingForClose()).containsExactly("моточасы", "краткое описание работ");

        assertThatThrownBy(() -> acts.close(opened.id())).isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("моточасы");
        ActDetail filled = header(withTime, new BigDecimal("1250.5"), "ТО-2000", null);
        ActDetail closed = acts.close(filled.id());
        assertThat(closed.status()).isEqualTo(ActStatus.CLOSED);
        assertThat(closed.permissions().canEdit()).isFalse();
        assertThat(directory.raisedMeters).anyMatch(s -> s.endsWith(":1250.5"));

        assertThatThrownBy(() -> header(closed, new BigDecimal("1300"), "Правка", null))
                .isInstanceOf(AccessDeniedException.class);

        login("admin-" + author, "ADMIN");
        assertThatThrownBy(() -> header(closed, new BigDecimal("1300"), "Правка", null))
                .isInstanceOf(BusinessRuleException.class).hasMessageContaining("закрытый акт");
        ActDetail fixed = header(closed, new BigDecimal("1300"), "ТО-2000 (исправлено)", "Опечатка в моточасах");
        assertThat(fixed.meterHours()).isEqualByComparingTo("1300");
        assertThatThrownBy(() -> acts.reopen(fixed.id(), " ")).isInstanceOf(BusinessRuleException.class);
        assertThat(acts.reopen(fixed.id(), "Нужно добавить запчасть").status()).isEqualTo(ActStatus.OPEN);
        assertThat(acts.history(fixed.id())).extracting(h -> h.action())
                .contains("CREATED", "OPENED", "CLOSED", "UPDATED_AFTER_CLOSE", "REOPENED");

        Integer events = jdbc.queryForObject("select count(*) from outbox where aggregate_id = ?", Integer.class,
                fixed.id());
        assertThat(events).isGreaterThanOrEqualTo(5);
    }

    @Test
    void participantsAndTimesheetRules() {
        String lead = user();
        String member = user();
        String outsider = user();
        directory.register(member);
        login(lead);
        ActDetail act = acts.open(acts.create(new ActCreateCommand(null, UUID.randomUUID())).id());
        act = content.addEngineer(act.id(), FakeDirectory.engineerId(member));
        assertThat(act.engineers()).hasSize(2);

        login(member);
        content.putTime(act.id(), FakeDirectory.engineerId(member), DAY, work("08:00-12:00"));
        UUID actId = act.id();
        assertThatThrownBy(() -> content.putTime(actId, FakeDirectory.engineerId(lead), DAY, work("08:00-12:00")))
                .isInstanceOf(AccessDeniedException.class);

        login(outsider);
        assertThatThrownBy(() -> content.putTime(actId, FakeDirectory.engineerId(outsider), DAY, work("08:00-12:00")))
                .isInstanceOf(AccessDeniedException.class);

        login(lead);
        UUID leadId = FakeDirectory.engineerId(lead);
        content.putTime(actId, leadId, DAY, work("02:00-22:00"));
        ActDetail other = acts.open(acts.create(new ActCreateCommand(null, UUID.randomUUID())).id());
        assertThatThrownBy(() -> content.putTime(other.id(), leadId, DAY, work("18:00-23:00")))
                .isInstanceOf(BusinessRuleException.class).hasMessageContaining("24");
        // Ночная смена 20:00–09:30 с перерывом через полночь 23:45–00:15 → 13 ч, предупреждение «больше 12 ч»
        var result = content.putTime(other.id(), leadId, DAY.plusDays(1), work("20:00-09:30", "23:45-00:15"));
        assertThat(result.warning()).contains("13");
        assertThatThrownBy(() -> content.putTime(other.id(), leadId, DAY.plusDays(3), work("10:00-13:00", "13:30-14:00")))
                .isInstanceOf(BusinessRuleException.class).hasMessageContaining("выходит за интервал");

        assertThatThrownBy(() -> content.removeEngineer(actId, FakeDirectory.engineerId(member)))
                .isInstanceOf(ConflictException.class);
        content.deleteTime(actId, FakeDirectory.engineerId(member), DAY);
        assertThat(content.removeEngineer(actId, FakeDirectory.engineerId(member)).engineers()).hasSize(1);
        assertThatThrownBy(() -> content.removeEngineer(actId, leadId)).isInstanceOf(ConflictException.class);
    }

    @Test
    void partsAndCopy() {
        login(user());
        ActDetail act = acts.create(new ActCreateCommand(null, UUID.randomUUID()));
        act = acts.updateHeader(act.id(), new ActHeaderCommand(act.version(), act.equipment().id(), null, null, null,
                null, null, "Замена ГМП", null, null, null, null, null, null, null));
        UUID id = act.id();
        content.addPart(id, new PartLineCommand(null, PartSource.CATALOG, UUID.randomUUID(), "Фильтр", "1r-0750",
                new BigDecimal("2"), "PCS", null, null));
        content.addPart(id, new PartLineCommand(null, PartSource.MANUAL, null, "Масло 15W-40", null,
                new BigDecimal("45.5"), "L", null, false));
        ActDetail withParts = content.addPart(id, new PartLineCommand(null, PartSource.MANUAL, null, "Прокладка",
                "X-1", BigDecimal.ONE, "PCS", null, true));
        assertThat(withParts.parts()).extracting(p -> p.position()).containsExactly(1, 2, 3);
        assertThat(withParts.parts().get(0).catalogNumber()).isEqualTo("1R-0750");
        assertThat(withParts.parts().get(1).unitName()).isEqualTo("л");
        assertThat(withParts.parts().get(2).source()).isEqualTo(PartSource.CATALOG);
        assertThatThrownBy(() -> content.addPart(id, new PartLineCommand(null, PartSource.MANUAL, null, "Болт", null,
                new BigDecimal("1.5"), "PCS", null, null))).isInstanceOf(BusinessRuleException.class);

        ActDetail copy = acts.copy(id);
        assertThat(copy.id()).isNotEqualTo(id);
        assertThat(copy.status()).isEqualTo(ActStatus.DRAFT);
        assertThat(copy.shortDescription()).isEqualTo("Замена ГМП");
        assertThat(copy.parts()).hasSize(3);
        assertThat(copy.timeEntries()).isEmpty();

        acts.delete(copy.id());
        ActDetail opened = acts.open(id);
        assertThatThrownBy(() -> acts.delete(opened.id())).isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void searchFindsByNumberTextAndParticipant() {
        String author = user();
        login(author);
        String marker = "Поиск-" + UUID.randomUUID().toString().substring(0, 6);
        ActDetail act = acts.create(new ActCreateCommand(null, UUID.randomUUID()));
        act = acts.updateHeader(act.id(), new ActHeaderCommand(act.version(), act.equipment().id(), null, null, null,
                null, null, marker, null, null, null, null, null, null, null));
        ActDetail opened = acts.open(act.id());

        var byText = acts.search(new ActSearchQuery(marker.toLowerCase(), null, null, null, null, null, null, null),
                PageQuery.of(0, 10, null));
        assertThat(byText.items()).singleElement().satisfies(s -> {
            assertThat(s.number()).isEqualTo(opened.number());
            assertThat(s.organizationName()).isEqualTo("АО «Тест»");
            assertThat(s.engineerNames()).contains(author);
        });
        assertThat(acts.search(new ActSearchQuery(opened.number(), null, null, null, null, null, null, null),
                PageQuery.of(0, 10, null)).totalElements()).isEqualTo(1);
        assertThat(acts.search(new ActSearchQuery(marker, EnumSet.of(ActStatus.CLOSED), null, null, null, null, null,
                null), PageQuery.of(0, 10, null)).totalElements()).isZero();
        assertThat(acts.searchMine(new ActSearchQuery(null, null, null, null, null, null, null, null),
                PageQuery.of(0, 50, "-createdAt")).items()).extracting(s -> s.id()).contains(opened.id());
        assertThat(acts.shortDescriptions(marker.substring(0, 8))).contains(marker);

        login(user());
        assertThat(acts.searchMine(new ActSearchQuery(null, null, null, null, null, null, null, null),
                PageQuery.of(0, 50, null)).items()).extracting(s -> s.id()).doesNotContain(opened.id());
    }
}
