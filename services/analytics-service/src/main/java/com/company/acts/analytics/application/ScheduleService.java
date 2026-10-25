package com.company.acts.analytics.application;

import com.company.acts.analytics.application.port.out.CalendarStore;
import com.company.acts.analytics.application.port.out.CurrentUserProvider;
import com.company.acts.analytics.application.port.out.DirectoryClient;
import com.company.acts.analytics.application.port.out.ScheduleStore;
import com.company.acts.analytics.application.view.DashboardView;
import com.company.acts.analytics.application.view.ScheduleViews.CoverageCell;
import com.company.acts.analytics.application.view.ScheduleViews.DayCell;
import com.company.acts.analytics.application.view.ScheduleViews.EmployeeRow;
import com.company.acts.analytics.application.view.ScheduleViews.Grid;
import com.company.acts.analytics.application.view.ScheduleViews.GroupView;
import com.company.acts.analytics.application.view.ScheduleViews.MemberView;
import com.company.acts.analytics.application.view.ScheduleViews.TemplateView;
import com.company.acts.analytics.domain.WorkCalendar;
import com.company.acts.analytics.domain.schedule.Crew;
import com.company.acts.analytics.domain.schedule.Member;
import com.company.acts.analytics.domain.schedule.ScheduleGenerator;
import com.company.acts.analytics.domain.schedule.ScheduleMode;
import com.company.acts.analytics.domain.schedule.ScheduleTemplate;
import com.company.acts.analytics.domain.schedule.ShiftKind;
import com.company.acts.analytics.domain.schedule.ShiftTime;
import com.company.acts.analytics.domain.schedule.Track;
import com.company.acts.common.core.BusinessRuleException;
import com.company.acts.common.core.ConflictException;
import com.company.acts.common.core.NotFoundException;
import com.company.acts.common.core.Strings;
import com.company.acts.common.core.Uuid7;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Рабочие графики (раздел 19.15 SPEC.md): конструктор режимов, группы и бригады, построение графика, смещение заезда
 * со сменщиками, ручные отметки, покрытие смен. Составляют и утверждают администратор и руководители (руководитель —
 * группы своего отдела); смотреть графики могут все.
 */
@Service
public class ScheduleService {

    public record TemplateCommand(String name, ScheduleMode mode, Integer workDays, Integer offDays, LocalTime dayStart,
                                  LocalTime dayEnd, Integer dayBreakMinutes, LocalTime nightStart, LocalTime nightEnd,
                                  Integer nightBreakMinutes, Integer switchAfterDays, Boolean restAfterNights,
                                  Integer overlapDays, Boolean dayPriority) {
    }

    public record GroupCommand(Long version, String name, UUID departmentId, UUID siteId, String siteName,
                               UUID templateId, LocalDate startDate, Integer requiredDay, Integer requiredNight,
                               List<UUID> employeeIds) {
    }

    public record MemberInput(UUID employeeId, Crew crew, Track track, UUID partnerId) {
    }

    private static final int MAX_DAYS = 400;

    private final ScheduleStore store;
    private final CalendarStore calendar;
    private final DirectoryClient directory;
    private final AccessScope access;
    private final CurrentUserProvider users;

    public ScheduleService(ScheduleStore store, CalendarStore calendar, DirectoryClient directory, AccessScope access,
                           CurrentUserProvider users) {
        this.store = store;
        this.calendar = calendar;
        this.directory = directory;
        this.access = access;
        this.users = users;
    }

    // ---------- Шаблоны (конструктор) ----------

    @Transactional(readOnly = true)
    public List<TemplateView> templates() {
        return store.templates().stream().map(t -> templateView(t.id(), t.template())).toList();
    }

    @Transactional
    public TemplateView saveTemplate(UUID id, TemplateCommand c) {
        requireEditor();
        ScheduleTemplate template = toTemplate(c);
        UUID key = id != null ? id : Uuid7.next();
        if (id != null) {
            store.template(id).orElseThrow(() -> NotFoundException.of("Шаблон графика", id));
        }
        store.saveTemplate(key, template, users.current().username());
        return templateView(key, template);
    }

    @Transactional
    public void deleteTemplate(UUID id) {
        requireEditor();
        if (store.templateInUse(id)) {
            throw new ConflictException("Шаблон используется в графиках групп — сначала смените шаблон у групп");
        }
        store.deleteTemplate(id);
    }

    // ---------- Группы ----------

    @Transactional(readOnly = true)
    public List<GroupView> groups() {
        AccessScope.Visible visible = access.resolve();
        Map<UUID, ScheduleStore.StoredTemplate> templates = templatesById();
        return store.groups().stream().map(g -> groupView(g, templates, visible)).toList();
    }

    @Transactional(readOnly = true)
    public GroupView group(UUID id) {
        return groupView(load(id), templatesById(), access.resolve());
    }

    /** Новая группа: сотрудники в заданном порядке распределяются по бригадам и сменам автоматически. */
    @Transactional
    public GroupView createGroup(GroupCommand c) {
        AccessScope.Visible visible = access.resolve();
        requireEdit(visible, c.departmentId());
        ScheduleTemplate template = templateOf(c.templateId());
        List<UUID> ids = distinct(c.employeeIds());
        List<Member> members = ScheduleGenerator.assign(template, ids, names(ids));
        int[] required = defaultRequired(template, members);
        ScheduleStore.Group group = new ScheduleStore.Group(Uuid7.next(), name(c.name()), c.departmentId(), c.siteId(),
                Strings.trimToNull(c.siteName()), c.templateId(), start(c.startDate()),
                c.requiredDay() != null ? c.requiredDay() : required[0],
                c.requiredNight() != null ? c.requiredNight() : required[1], null, null, -1);
        store.saveGroup(group);
        store.replaceMembers(group.id(), members);
        return group(group.id());
    }

    /**
     * Изменить параметры группы. Если изменился состав сотрудников или шаблон — состав распределяется заново
     * (смещения и ручные отметки оставшихся сохраняются по сотрудникам). Утверждение снимается.
     */
    @Transactional
    public GroupView updateGroup(UUID id, GroupCommand c) {
        ScheduleStore.Group current = load(id);
        AccessScope.Visible visible = access.resolve();
        requireEdit(visible, current.departmentId());
        requireEdit(visible, c.departmentId());
        ScheduleTemplate template = templateOf(c.templateId());
        List<Member> members = store.members(id);
        List<UUID> ids = c.employeeIds() == null ? members.stream().map(Member::employeeId).toList()
                : distinct(c.employeeIds());
        boolean reassign = !c.templateId().equals(current.templateId())
                || !new HashSet<>(ids).equals(members.stream().map(Member::employeeId).collect(Collectors.toSet()));
        if (reassign) {
            Map<UUID, Integer> offsets = members.stream().collect(Collectors.toMap(Member::employeeId, Member::offsetDays));
            members = ScheduleGenerator.assign(template, ids, names(ids)).stream()
                    .map(m -> m.withOffset(offsets.getOrDefault(m.employeeId(), 0))).toList();
            store.replaceMembers(id, members);
        }
        int[] required = defaultRequired(template, members);
        store.saveGroup(new ScheduleStore.Group(id, name(c.name()), c.departmentId(), c.siteId(),
                Strings.trimToNull(c.siteName()), c.templateId(), start(c.startDate()),
                c.requiredDay() != null ? c.requiredDay() : required[0],
                c.requiredNight() != null ? c.requiredNight() : required[1], null, null,
                c.version() != null ? c.version() : current.version()));
        return group(id);
    }

    @Transactional
    public void deleteGroup(UUID id) {
        requireEdit(access.resolve(), load(id).departmentId());
        store.deleteGroup(id);
    }

    /** Ручная правка состава: бригада, порядок смен, сменщик. Сменщик — из другой бригады, пара взаимна. */
    @Transactional
    public GroupView updateMembers(UUID id, List<MemberInput> inputs) {
        ScheduleStore.Group g = load(id);
        requireEdit(access.resolve(), g.departmentId());
        Map<UUID, Member> current = store.members(id).stream()
                .collect(Collectors.toMap(Member::employeeId, Function.identity()));
        Map<UUID, MemberInput> byId = new HashMap<>();
        for (MemberInput in : inputs) {
            if (!current.containsKey(in.employeeId())) {
                throw new BusinessRuleException("employeeId", "Сотрудник не состоит в группе");
            }
            byId.put(in.employeeId(), in);
        }
        for (MemberInput in : inputs) {
            if (in.partnerId() == null) {
                continue;
            }
            MemberInput partner = byId.get(in.partnerId());
            Crew partnerCrew = partner != null ? partner.crew() : current.get(in.partnerId()) != null
                    ? current.get(in.partnerId()).crew() : null;
            if (partnerCrew == null || partnerCrew == in.crew()) {
                throw new BusinessRuleException("partnerId", "Сменщик должен быть из другой бригады этой группы");
            }
        }
        List<Member> updated = store.members(id).stream().map(m -> {
            MemberInput in = byId.get(m.employeeId());
            return in == null ? m : new Member(m.employeeId(), m.name(), in.crew(), in.track(), m.offsetDays(),
                    in.partnerId());
        }).toList();
        store.replaceMembers(id, updated);
        unapprove(g);
        return group(id);
    }

    /** Сместить заезд: сотрудники и их сменщики сдвигаются на одинаковое число дней (раздел 19.15). */
    @Transactional
    public GroupView shiftArrival(UUID id, Set<UUID> employeeIds, int days) {
        ScheduleStore.Group g = load(id);
        requireEdit(access.resolve(), g.departmentId());
        if (days == 0 || Math.abs(days) > 366) {
            throw new BusinessRuleException("days", "Смещение — от 1 до 366 дней в любую сторону");
        }
        store.replaceMembers(id, ScheduleGenerator.shiftArrival(store.members(id), employeeIds, days));
        unapprove(g);
        return group(id);
    }

    /** Ручная отметка на день или период: отпуск, больничный, разовое продление вахты; kind = null — снять. */
    @Transactional
    public void mark(UUID id, UUID employeeId, LocalDate from, LocalDate to, ShiftKind kind, String comment) {
        ScheduleStore.Group g = load(id);
        requireEdit(access.resolve(), g.departmentId());
        if (store.members(id).stream().noneMatch(m -> m.employeeId().equals(employeeId))) {
            throw new BusinessRuleException("employeeId", "Сотрудник не состоит в группе");
        }
        LocalDate end = to == null ? from : to;
        if (from == null || end.isBefore(from) || from.plusDays(MAX_DAYS).isBefore(end)) {
            throw new BusinessRuleException("from", "Укажите период отметки (не больше года)");
        }
        for (LocalDate d = from; !d.isAfter(end); d = d.plusDays(1)) {
            store.setOverride(id, employeeId, d, kind, Strings.trimToNull(comment), users.current().username());
        }
        unapprove(g);
    }

    /** Утвердить график: руководитель подтверждает, что график проверен. Любая правка снимает утверждение. */
    @Transactional
    public GroupView approve(UUID id) {
        ScheduleStore.Group g = load(id);
        requireEdit(access.resolve(), g.departmentId());
        store.saveGroup(new ScheduleStore.Group(g.id(), g.name(), g.departmentId(), g.siteId(), g.siteName(),
                g.templateId(), g.startDate(), g.requiredDay(), g.requiredNight(), Instant.now(),
                users.current().username(), g.version()));
        return group(id);
    }

    // ---------- График на период ----------

    @Transactional(readOnly = true)
    public Grid grid(UUID id, LocalDate from, LocalDate to) {
        if (from == null || to == null || to.isBefore(from) || from.plusDays(MAX_DAYS).isBefore(to)) {
            throw new BusinessRuleException("to", "Период — не больше года");
        }
        ScheduleStore.Group g = load(id);
        ScheduleTemplate template = templateOf(g.templateId());
        List<Member> members = store.members(id);
        Map<UUID, Map<LocalDate, ShiftKind>> overrides = store.overrides(id, from.minusDays(1), to);
        ScheduleGenerator generator = new ScheduleGenerator(template, g.startDate(), members, overrides,
                new WorkCalendar(calendar.overrides(from.minusDays(1), to)), g.requiredDay(), g.requiredNight());
        List<EmployeeRow> rows = new ArrayList<>();
        for (Member m : members) {
            List<DayCell> days = new ArrayList<>();
            int total = 0;
            int shifts = 0;
            Map<LocalDate, ShiftKind> manual = overrides.getOrDefault(m.employeeId(), Map.of());
            for (LocalDate d = from; !d.isAfter(to); d = d.plusDays(1)) {
                ShiftKind kind = generator.kind(m.employeeId(), d);
                int minutes = generator.plannedMinutes(m.employeeId(), d);
                total += minutes;
                if (kind.working()) {
                    shifts++;
                }
                days.add(new DayCell(d, kind, kind.letter(), hours(minutes), manual.containsKey(d)));
            }
            rows.add(new EmployeeRow(m.employeeId(), m.name(), m.crew(), m.track(), days, hours(total), shifts));
        }
        List<CoverageCell> coverage = generator.coverage(from, to).stream()
                .map(c -> new CoverageCell(c.date(), c.day(), c.night(), c.requiredDay(), c.requiredNight(), c.shortage()))
                .toList();
        return new Grid(id, g.name(), from, to, rows, coverage, (int) coverage.stream().filter(CoverageCell::shortage).count());
    }

    /**
     * Норма сотрудника по графикам за период, минуты по дням (для норм и контроля превышений, раздел 19.15).
     * Пусто — сотрудник ни в одной группе.
     */
    @Transactional(readOnly = true)
    public Map<LocalDate, Integer> plannedMinutes(UUID employeeId, LocalDate from, LocalDate to) {
        Map<LocalDate, Integer> result = new HashMap<>();
        WorkCalendar cal = new WorkCalendar(calendar.overrides(from.minusDays(1), to));
        for (UUID groupId : store.groupsOf(employeeId)) {
            ScheduleStore.Group g = load(groupId);
            ScheduleGenerator generator = new ScheduleGenerator(templateOf(g.templateId()), g.startDate(),
                    store.members(groupId), store.overrides(groupId, from.minusDays(1), to), cal, g.requiredDay(),
                    g.requiredNight());
            for (LocalDate d = from; !d.isAfter(to); d = d.plusDays(1)) {
                result.merge(d, generator.plannedMinutes(employeeId, d), Integer::sum);
            }
        }
        return result;
    }

    // ---------- Вспомогательное ----------

    private TemplateView templateView(UUID id, ScheduleTemplate t) {
        return new TemplateView(id, t.name(), t.mode(), t.workDays(), t.offDays(), t.day().start(), t.day().end(),
                t.day().breakMinutes(), t.night() == null ? null : t.night().start(),
                t.night() == null ? null : t.night().end(), t.night() == null ? null : t.night().breakMinutes(),
                t.switchAfterDays(), t.restAfterNights(), t.overlapDays(), t.dayPriority(), hours(t.day().paidMinutes()),
                t.night() == null ? null : hours(t.night().paidMinutes()), t.continuityWarning(),
                store.templateInUse(id));
    }

    private GroupView groupView(ScheduleStore.Group g, Map<UUID, ScheduleStore.StoredTemplate> templates,
                                AccessScope.Visible visible) {
        List<Member> members = store.members(g.id());
        Map<UUID, String> names = members.stream().collect(Collectors.toMap(Member::employeeId,
                m -> m.name() == null ? "—" : m.name()));
        ScheduleStore.StoredTemplate t = templates.get(g.templateId());
        String warning = t == null ? null : t.template().continuityWarning();
        if (t != null && t.template().mode() == ScheduleMode.ROTATION && members.size() % 2 == 1) {
            warning = (warning == null ? "" : warning + "; ") + "нечётное число сотрудников: в бригаде Б на одного меньше";
        }
        return new GroupView(g.id(), g.name(), g.departmentId(), g.siteId(), g.siteName(), g.templateId(),
                t == null ? null : t.template().name(), g.startDate(), g.requiredDay(), g.requiredNight(),
                members.stream().map(m -> new MemberView(m.employeeId(), m.name(), m.crew(), m.track(), m.offsetDays(),
                        m.partnerId(), m.partnerId() == null ? null : names.get(m.partnerId()))).toList(),
                g.approvedAt(), g.approvedBy(), g.version(), visible.canEditDepartment(g.departmentId()), warning);
    }

    private static ScheduleTemplate toTemplate(TemplateCommand c) {
        try {
            ScheduleMode mode = c.mode() == null ? ScheduleMode.FIVE_TWO : c.mode();
            ShiftTime day = new ShiftTime(c.dayStart(), c.dayEnd(), c.dayBreakMinutes() == null ? 0 : c.dayBreakMinutes());
            ShiftTime night = mode == ScheduleMode.ROTATION && c.nightStart() != null && c.nightEnd() != null
                    ? new ShiftTime(c.nightStart(), c.nightEnd(), c.nightBreakMinutes() == null ? 0 : c.nightBreakMinutes())
                    : null;
            return new ScheduleTemplate(name(c.name()), mode, c.workDays() == null ? 5 : c.workDays(),
                    c.offDays() == null ? 2 : c.offDays(), day, night,
                    c.switchAfterDays() == null ? 0 : c.switchAfterDays(), Boolean.TRUE.equals(c.restAfterNights()),
                    c.overlapDays() == null ? 0 : c.overlapDays(), c.dayPriority() == null || c.dayPriority());
        } catch (IllegalArgumentException e) {
            throw new BusinessRuleException("template", e.getMessage());
        }
    }

    /** Сколько человек нужно днём и ночью по умолчанию: как в полной бригаде (или все — при 5/2). */
    private static int[] defaultRequired(ScheduleTemplate t, List<Member> members) {
        if (t.mode() == ScheduleMode.FIVE_TWO) {
            return new int[] {members.size(), 0};
        }
        long crewA = members.stream().filter(m -> m.crew() == Crew.A).count();
        return ScheduleGenerator.split(t, (int) crewA);
    }

    private ScheduleStore.Group load(UUID id) {
        return store.group(id).orElseThrow(() -> NotFoundException.of("График группы", id));
    }

    private ScheduleTemplate templateOf(UUID id) {
        if (id == null) {
            throw new BusinessRuleException("templateId", "Выберите шаблон графика");
        }
        return store.template(id).map(ScheduleStore.StoredTemplate::template)
                .orElseThrow(() -> new BusinessRuleException("templateId", "Шаблон графика не найден"));
    }

    private Map<UUID, ScheduleStore.StoredTemplate> templatesById() {
        return store.templates().stream().collect(Collectors.toMap(ScheduleStore.StoredTemplate::id, Function.identity()));
    }

    private Map<UUID, String> names(List<UUID> ids) {
        Map<UUID, String> names = directory.engineers().stream()
                .collect(Collectors.toMap(DirectoryClient.EngineerRef::id, DirectoryClient.EngineerRef::fullName,
                        (a, b) -> a));
        for (UUID id : ids) {
            if (!names.containsKey(id)) {
                throw new BusinessRuleException("employeeIds", "Сотрудник не найден в справочнике: " + id);
            }
        }
        return names;
    }

    private void unapprove(ScheduleStore.Group g) {
        if (g.approvedAt() != null) {
            ScheduleStore.Group fresh = load(g.id());
            store.saveGroup(new ScheduleStore.Group(fresh.id(), fresh.name(), fresh.departmentId(), fresh.siteId(),
                    fresh.siteName(), fresh.templateId(), fresh.startDate(), fresh.requiredDay(), fresh.requiredNight(),
                    null, null, fresh.version()));
        }
    }

    private void requireEditor() {
        AccessScope.Visible visible = access.resolve();
        if (visible.scope() == DashboardView.Scope.SELF) {
            throw new AccessDeniedException("Графики составляют администратор и руководители");
        }
    }

    private static void requireEdit(AccessScope.Visible visible, UUID departmentId) {
        if (!visible.canEditDepartment(departmentId)) {
            throw new AccessDeniedException("Графики составляют администратор и руководитель своего отдела");
        }
    }

    private static List<UUID> distinct(List<UUID> ids) {
        if (ids == null || ids.isEmpty()) {
            throw new BusinessRuleException("employeeIds", "Добавьте сотрудников в группу");
        }
        return new ArrayList<>(new LinkedHashSet<>(ids.stream().filter(Objects::nonNull).toList()));
    }

    private static String name(String raw) {
        String name = Strings.trimToNull(raw);
        if (name == null) {
            throw new BusinessRuleException("name", "Укажите название");
        }
        return name;
    }

    private static LocalDate start(LocalDate date) {
        if (date == null) {
            throw new BusinessRuleException("startDate", "Укажите дату начала графика (заезд бригады А)");
        }
        return date;
    }

    static BigDecimal hours(int minutes) {
        return BigDecimal.valueOf(minutes).divide(BigDecimal.valueOf(60), 2, RoundingMode.HALF_UP).stripTrailingZeros();
    }
}
