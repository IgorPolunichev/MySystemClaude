package com.company.acts.act;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.company.acts.act.application.ActContentService;
import com.company.acts.act.application.ActPhotoService;
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
        FakeMedia fakeMedia() {
            return new FakeMedia();
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
    ActPhotoService photos;
    @Autowired
    FakeDirectory directory;
    @Autowired
    JdbcTemplate jdbc;
    @Autowired
    FakeMedia media;
    @Autowired
    com.company.acts.act.application.EmailSentHandler emailSent;
    @Autowired
    com.company.acts.act.application.ActAttachmentService attachments;

    /** Поддельный media-service: файл «загружен» для указанного владельца. */
    static class FakeMedia implements com.company.acts.act.application.port.out.MediaClient {
        final java.util.Map<UUID, UUID> owners = new java.util.concurrent.ConcurrentHashMap<>();

        UUID upload(UUID actId) {
            UUID id = UUID.randomUUID();
            owners.put(id, actId);
            return id;
        }

        @Override
        public MediaInfo media(UUID id) {
            return new MediaInfo(id, "ACT_ATTACHMENT", owners.get(id), "Акт подписан.pdf", "application/pdf", 245_000);
        }
    }

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
                adminComment, act.executor() == null ? null : act.executor().id(),
                act.site() == null ? null : act.site().id()));
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
                null, null, "Замена ГМП", null, null, null, null, null, null, null, null, null));
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
        // открытый акт тоже можно удалить (номер больше не выдаётся); посторонний — не может
        ActDetail opened = acts.open(id);
        login(user());
        assertThatThrownBy(() -> acts.delete(opened.id())).isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void searchFindsByNumberTextAndParticipant() {
        String author = user();
        login(author);
        String marker = "Поиск-" + UUID.randomUUID().toString().substring(0, 6);
        ActDetail act = acts.create(new ActCreateCommand(null, UUID.randomUUID()));
        act = acts.updateHeader(act.id(), new ActHeaderCommand(act.version(), act.equipment().id(), null, null, null,
                null, null, marker, null, null, null, null, null, null, null, null, null));
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

    @Test
    void photosNeedCaptionsBeforeClosing() {
        String author = user();
        login(author);
        ActDetail act = acts.open(acts.create(new ActCreateCommand(null, UUID.randomUUID())).id());
        act = header(act, new BigDecimal("100"), "Диагностика", null);
        content.putTime(act.id(), FakeDirectory.engineerId(author), DAY, work("09:00-12:00"));
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();
        photos.add(act.id(), first, "Течь масла из-под крышки клапанов");
        ActDetail withPhotos = photos.add(act.id(), second, null);
        assertThat(withPhotos.photos()).extracting(p -> p.position()).containsExactly(1, 2);
        assertThat(withPhotos.missingForClose()).anyMatch(m -> m.contains("подписи к фото"));
        UUID actId = act.id();
        assertThatThrownBy(() -> acts.close(actId)).isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("подписи к фото");

        UUID secondPhoto = withPhotos.photos().get(1).id();
        photos.caption(actId, secondPhoto, "Замена прокладки");
        ActDetail reordered = photos.reorder(actId, List.of(secondPhoto, withPhotos.photos().get(0).id()));
        assertThat(reordered.photos().get(0).caption()).isEqualTo("Замена прокладки");
        assertThat(reordered.missingForClose()).isEmpty();
        assertThatThrownBy(() -> photos.reorder(actId, List.of(secondPhoto)))
                .isInstanceOf(BusinessRuleException.class);

        assertThat(photos.add(actId, first, "дубль").photos()).hasSize(2);
        assertThat(acts.close(actId).status()).isEqualTo(ActStatus.CLOSED);
        assertThatThrownBy(() -> photos.delete(actId, secondPhoto)).isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void executorAndSiteAreSnapshottedAndBlockedSiteIsRejected() {
        login(user());
        ActDetail act = acts.create(new ActCreateCommand(null, UUID.randomUUID()));
        assertThat(act.executor().id()).isEqualTo(FakeDirectory.EXECUTOR_MAIN);
        assertThat(act.site().name()).isEqualTo("Площадка А");

        ActDetail changed = acts.updateHeader(act.id(), new ActHeaderCommand(act.version(), act.equipment().id(), null,
                null, null, null, null, "ТО", null, null, null, null, null, null, null, FakeDirectory.EXECUTOR_SECOND,
                FakeDirectory.SITE_B));
        assertThat(changed.executor().shortName()).isEqualTo("ООО «Второй»");
        assertThat(changed.site().name()).isEqualTo("Площадка Б");

        assertThatThrownBy(() -> acts.updateHeader(act.id(), new ActHeaderCommand(changed.version(),
                changed.equipment().id(), null, null, null, null, null, "ТО", null, null, null, null, null, null, null,
                FakeDirectory.EXECUTOR_SECOND, FakeDirectory.SITE_CLOSED)))
                .isInstanceOf(BusinessRuleException.class).hasMessageContaining("заблокирована");
        assertThat(acts.search(new ActSearchQuery("Площадка Б", null, null, null, null, null, null, null),
                PageQuery.of(0, 10, null)).items()).extracting(s -> s.id()).contains(act.id());
    }

    @Test
    void signedScanCanBeAttachedAfterClosing() {
        String author = user();
        login(author);
        ActDetail act = acts.open(acts.create(new ActCreateCommand(null, UUID.randomUUID())).id());
        act = header(act, new BigDecimal("50"), "Ремонт", null);
        content.putTime(act.id(), FakeDirectory.engineerId(author), DAY, work("09:00-11:00"));
        ActDetail closed = acts.close(act.id());
        assertThat(closed.permissions().canAttach()).isTrue();

        UUID scan = media.upload(closed.id());
        ActDetail withScan = attachments.add(closed.id(), scan,
                com.company.acts.act.domain.AttachmentKind.SIGNED_ACT, "Подписан механиком");
        assertThat(withScan.attachments()).singleElement().satisfies(a -> {
            assertThat(a.fileName()).isEqualTo("Акт подписан.pdf");
            assertThat(a.canDelete()).isTrue();
        });
        assertThat(acts.search(new ActSearchQuery(closed.number(), null, null, null, null, null, null, null),
                PageQuery.of(0, 10, null)).items()).singleElement().satisfies(s -> assertThat(s.hasSignedScan()).isTrue());

        UUID foreign = media.upload(UUID.randomUUID());
        UUID actId = closed.id();
        assertThatThrownBy(() -> attachments.add(actId, foreign, null, null))
                .isInstanceOf(BusinessRuleException.class).hasMessageContaining("не для этого акта");

        login(user());
        assertThatThrownBy(() -> attachments.add(actId, media.upload(actId), null, null))
                .isInstanceOf(AccessDeniedException.class);
        UUID attachmentId = withScan.attachments().get(0).id();
        assertThatThrownBy(() -> attachments.delete(actId, attachmentId)).isInstanceOf(AccessDeniedException.class);

        login(author);
        assertThat(attachments.delete(actId, attachmentId).attachments()).isEmpty();
        assertThat(acts.search(new ActSearchQuery(closed.number(), null, null, null, null, null, null, null),
                PageQuery.of(0, 10, null)).items().get(0).hasSignedScan()).isFalse();
    }

    @Test
    void siteCodeGoesIntoNumberAndUnclosedActsCanBeDeleted() {
        String author = user();
        login(author);
        ActDetail act = acts.create(new ActCreateCommand(null, UUID.randomUUID()));
        act = acts.updateHeader(act.id(), new ActHeaderCommand(act.version(), act.equipment().id(), null, null, null,
                null, null, "ТО", null, null, null, null, null, null, null, null, FakeDirectory.SITE_B));
        ActDetail first = acts.open(act.id());
        assertThat(first.number()).matches("АВР-Б-\\d{4}-\\d{6}");
        ActDetail secondDraft = acts.create(new ActCreateCommand(null, UUID.randomUUID()));
        secondDraft = acts.updateHeader(secondDraft.id(), new ActHeaderCommand(secondDraft.version(),
                secondDraft.equipment().id(), null, null, null, null, null, "ТО", null, null, null, null, null, null,
                null, null, FakeDirectory.SITE_B));
        ActDetail second = acts.open(secondDraft.id());
        long n1 = Long.parseLong(first.number().substring(first.number().length() - 6));
        assertThat(second.number()).endsWith("%06d".formatted(n1 + 1));

        assertThat(first.permissions().canDelete()).isTrue();
        acts.delete(first.id());
        UUID deleted = first.id();
        assertThatThrownBy(() -> acts.get(deleted)).isInstanceOf(com.company.acts.common.core.NotFoundException.class);

        ActDetail cancelled = acts.cancel(second.id(), "Ошибочно заведён");
        jdbc.update("update work_act set updated_at = now() - interval '40 days' where id = ?", cancelled.id());
        assertThat(acts.purgeCancelled(java.time.Duration.ofDays(30))).isGreaterThanOrEqualTo(1);
        UUID purged = cancelled.id();
        assertThatThrownBy(() -> acts.get(purged)).isInstanceOf(com.company.acts.common.core.NotFoundException.class);
    }

    @Test
    void fullTextSearchUsesMorphologyRankingAndPartNumbers() {
        String author = user();
        login(author);
        String marker = "zq" + UUID.randomUUID().toString().substring(0, 6);
        ActDetail a = acts.create(new ActCreateCommand(null, UUID.randomUUID()));
        a = acts.updateHeader(a.id(), new ActHeaderCommand(a.version(), a.equipment().id(), null, null, null, null, null,
                "Замена форсунок", marker + " заменены четыре форсунки двигателя", null, null, null, null, null, null,
                a.executor().id(), a.site().id()));
        content.addPart(a.id(), new PartLineCommand(null, PartSource.CATALOG, UUID.randomUUID(), "Форсунка топливная",
                "7E-6371", new BigDecimal("4"), "PCS", null, null));
        ActDetail b = acts.create(new ActCreateCommand(null, UUID.randomUUID()));
        b = acts.updateHeader(b.id(), new ActHeaderCommand(b.version(), b.equipment().id(), null, null, null, null, null,
                "Диагностика", marker + " проверены форсунки и насос", null, null, null, null, null, null,
                b.executor().id(), b.site().id()));

        // морфология: «форсунками» находит «форсунок» и «форсунки»; оба акта, акт с совпадением в кратком описании — выше
        var found = acts.search(new ActSearchQuery("форсунками " + marker, null, null, null, null, null, null, null),
                PageQuery.of(0, 10, null));
        assertThat(found.items()).extracting(x -> x.id()).containsExactly(a.id(), b.id());
        assertThat(found.items().get(1).highlight()).contains("[[");

        // часть каталожного номера — и в общем поиске, и в фильтре по запчасти
        assertThat(acts.search(new ActSearchQuery("6371", null, null, null, null, null, null, null),
                PageQuery.of(0, 50, null)).items()).extracting(x -> x.id()).contains(a.id());
        var byPart = acts.search(new ActSearchQuery(marker, null, null, null, null, null, null, null, null, "7e-63",
                null, null), PageQuery.of(0, 10, null));
        assertThat(byPart.items()).extracting(x -> x.id()).containsExactly(a.id());

        // точное краткое описание, площадка, отметка о скане; сортировка по инженерам
        var exact = acts.search(new ActSearchQuery(marker, null, null, null, null, null, null, null,
                FakeDirectory.SITE_A, null, "Диагностика", false), PageQuery.of(0, 10, "engineerNames"));
        assertThat(exact.items()).extracting(x -> x.id()).containsExactly(b.id());
        assertThat(exact.items().get(0).siteName()).isEqualTo("Площадка А");

        // ФИО инженера и подпись к фото тоже участвуют в поиске
        assertThat(acts.search(new ActSearchQuery("уплотнение " + marker, null, null, null, null, null, null, null),
                PageQuery.of(0, 10, null)).items()).isEmpty();
        photos.add(b.id(), UUID.randomUUID(), "Износ уплотнения насоса");
        assertThat(acts.search(new ActSearchQuery("уплотнение " + marker, null, null, null, null, null, null, null),
                PageQuery.of(0, 10, null)).items()).extracting(x -> x.id()).containsExactly(b.id());
        assertThat(acts.search(new ActSearchQuery("Инженер " + author, null, null, null, null, null, null, null),
                PageQuery.of(0, 10, null)).items()).extracting(x -> x.id()).contains(a.id(), b.id());
    }

    @Test
    void emailSentEventMarksActsOnce() {
        login(user());
        ActDetail act = acts.create(new ActCreateCommand(null, UUID.randomUUID()));
        assertThat(act.lastSentAt()).isNull();
        java.util.Map<String, Object> envelope = new java.util.LinkedHashMap<>();
        envelope.put("eventId", UUID.randomUUID().toString());
        envelope.put("eventType", "EmailSent");
        envelope.put("aggregateId", UUID.randomUUID().toString());
        envelope.put("payload", java.util.Map.of("actIds", List.of(act.id().toString()), "sentAt", "2026-10-20T09:15:00Z",
                "mode", "SMTP"));
        emailSent.handle(envelope);
        emailSent.handle(envelope);
        ActDetail sent = acts.get(act.id());
        assertThat(sent.lastSentAt()).isEqualTo(java.time.Instant.parse("2026-10-20T09:15:00Z"));
        assertThat(jdbc.queryForObject("select sent_count from work_act where id = ?", Integer.class, act.id())).isEqualTo(1);
        assertThat(acts.search(new ActSearchQuery(null, null, null, null, null, null, null, null), PageQuery.of(0, 200, null))
                .items()).filteredOn(x -> x.id().equals(act.id())).singleElement()
                .satisfies(x -> assertThat(x.lastSentAt()).isNotNull());
    }

    @Test
    void deviceSetContainsOnlyMyActs() {
        String me = user();
        login(me);
        ActDetail mine = acts.create(new ActCreateCommand(null, UUID.randomUUID()));
        String other = user();
        login(other);
        ActDetail foreign = acts.create(new ActCreateCommand(null, UUID.randomUUID()));
        login(me);
        assertThat(acts.forDevice(90)).extracting(ActDetail::id).contains(mine.id()).doesNotContain(foreign.id());
        // ПК площадки: чужие открытые акты этой площадки выгружаются, чужие черновики — нет
        login(other);
        ActDetail foreignOpen = acts.open(acts.create(new ActCreateCommand(null, UUID.randomUUID())).id());
        login(me);
        assertThat(acts.forDevice(90, List.of(FakeDirectory.SITE_A), List.of())).extracting(ActDetail::id)
                .contains(mine.id(), foreignOpen.id()).doesNotContain(foreign.id());
        // повтор создания с тем же id (повторная отправка из очереди устройства) не создаёт дубль
        UUID clientId = UUID.randomUUID();
        ActDetail first = acts.create(new ActCreateCommand(clientId, UUID.randomUUID()));
        ActDetail again = acts.create(new ActCreateCommand(clientId, UUID.randomUUID()));
        assertThat(again.id()).isEqualTo(first.id()).isEqualTo(clientId);
    }

    @Test
    void sharedActCanBeContinuedAndClosedByAnotherEngineer() {
        String author = user();
        login(author);
        ActDetail act = acts.open(acts.create(new ActCreateCommand(null, UUID.randomUUID())).id());
        act = header(act, new BigDecimal("120"), "ТО", null);
        assertThat(act.permissions().canShare()).isTrue();

        String next = user();
        login(next);
        UUID actId = act.id();
        ActDetail seen = acts.get(actId);
        assertThat(seen.permissions().canEdit()).isFalse();
        assertThat(seen.permissions().canShare()).isFalse();
        assertThatThrownBy(() -> acts.share(actId, true)).isInstanceOf(AccessDeniedException.class);

        login(author);
        assertThat(acts.share(actId, true).openForAll()).isTrue();

        login(next);
        ActDetail shared = acts.get(actId);
        assertThat(shared.permissions().canEdit()).isTrue();
        assertThat(shared.permissions().canClose()).isTrue();
        content.addEngineer(actId, FakeDirectory.engineerId(next));
        content.putTime(actId, FakeDirectory.engineerId(next), DAY, work("09:00-12:00"));
        assertThat(acts.close(actId).status()).isEqualTo(ActStatus.CLOSED);

        // администратор правит закрытый акт независимо от доступа
        login(user(), "ADMIN");
        assertThat(acts.get(actId).permissions().canEdit()).isTrue();
    }

    @Test
    void siteIsFixedAfterOpeningAndMeterHoursAreWhole() {
        login(user());
        ActDetail draft = acts.create(new ActCreateCommand(null, UUID.randomUUID()));
        // в черновике площадку менять можно
        ActDetail moved = acts.updateHeader(draft.id(), new ActHeaderCommand(draft.version(), draft.equipment().id(), null,
                null, null, null, null, "ТО", null, null, null, null, null, null, null, null, FakeDirectory.SITE_B));
        assertThat(moved.site().name()).isEqualTo("Площадка Б");
        ActDetail opened = acts.open(moved.id());
        assertThatThrownBy(() -> acts.updateHeader(opened.id(), new ActHeaderCommand(opened.version(),
                opened.equipment().id(), null, null, null, null, null, "ТО", null, null, null, null, null, null, null, null,
                FakeDirectory.SITE_A))).isInstanceOf(BusinessRuleException.class).hasMessageContaining("Площадку открытого акта");
        assertThatThrownBy(() -> acts.updateHeader(opened.id(), new ActHeaderCommand(opened.version(),
                opened.equipment().id(), new BigDecimal("1250.5"), null, null, null, null, "ТО", null, null, null, null, null,
                null, null, null, FakeDirectory.SITE_B))).isInstanceOf(BusinessRuleException.class).hasMessageContaining("целое");
        ActDetail ok = acts.updateHeader(opened.id(), new ActHeaderCommand(opened.version(), opened.equipment().id(),
                new BigDecimal("1250.0"), null, null, null, null, "ТО", null, null, null, null, null, null, null, null,
                FakeDirectory.SITE_B));
        assertThat(ok.meterHours()).isEqualByComparingTo("1250");
    }
}
