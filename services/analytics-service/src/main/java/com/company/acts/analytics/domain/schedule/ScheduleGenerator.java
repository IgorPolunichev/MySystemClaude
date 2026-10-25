package com.company.acts.analytics.domain.schedule;

import com.company.acts.analytics.domain.WorkCalendar;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Построение графика группы (раздел 19.15 SPEC.md). График не хранится по дням: он вычисляется из параметров
 * конструктора, состава бригад, смещений заезда и ручных отметок — поэтому строится на любой период (год вперёд).
 *
 * <p>Сменный/вахтовый N/M: цикл N + M дней. Бригада А заезжает в дату начала, бригада Б — через N − k дней (k —
 * пересекающиеся дни). Смещение заезда сотрудника сдвигает его цикл. При ночных сменах подгруппа «сначала день»
 * работает днём первые s дней и ночью остальные; «сначала ночь» — наоборот, с отсыпным при переходе на дневные,
 * если он включён. 5/2 — рабочие дни производственного календаря, дневные смены.
 */
public final class ScheduleGenerator {

    /** Покрытие смен за сутки: сколько людей днём и ночью и сколько нужно. */
    public record Coverage(LocalDate date, int day, int night, int requiredDay, int requiredNight) {
        public boolean shortage() {
            return day < requiredDay || night < requiredNight;
        }
    }

    private final ScheduleTemplate template;
    private final LocalDate start;
    private final Map<UUID, Member> members = new HashMap<>();
    private final Map<UUID, Map<LocalDate, ShiftKind>> overrides;
    private final WorkCalendar calendar;
    private final int requiredDay;
    private final int requiredNight;

    public ScheduleGenerator(ScheduleTemplate template, LocalDate start, Collection<Member> members,
                             Map<UUID, Map<LocalDate, ShiftKind>> overrides, WorkCalendar calendar, int requiredDay,
                             int requiredNight) {
        this.template = template;
        this.start = start;
        members.forEach(m -> this.members.put(m.employeeId(), m));
        this.overrides = overrides == null ? Map.of() : overrides;
        this.calendar = calendar;
        this.requiredDay = requiredDay;
        this.requiredNight = requiredNight;
    }

    /** Смена сотрудника в этот день с учётом ручных отметок. */
    public ShiftKind kind(UUID employeeId, LocalDate date) {
        ShiftKind manual = overrides.getOrDefault(employeeId, Map.of()).get(date);
        return manual != null ? manual : planned(members.get(employeeId), date);
    }

    /** Смена по правилам конструктора, без ручных отметок. */
    public ShiftKind planned(Member m, LocalDate date) {
        if (m == null) {
            return ShiftKind.OFF;
        }
        if (template.mode() == ScheduleMode.FIVE_TWO) {
            return calendar.isWorking(date) ? ShiftKind.DAY : ShiftKind.OFF;
        }
        int crewOffset = m.crew() == Crew.A ? 0 : template.workDays() - template.overlapDays();
        long since = ChronoUnit.DAYS.between(start.plusDays(crewOffset + m.offsetDays()), date);
        int t = (int) Math.floorMod(since, (long) template.cycleDays());
        if (t >= template.workDays()) {
            return ShiftKind.OFF;
        }
        if (!template.hasNights()) {
            return ShiftKind.DAY;
        }
        int s = template.switchAfterDays();
        return switch (m.track()) {
            case FIXED_DAY -> ShiftKind.DAY;
            case FIXED_NIGHT -> ShiftKind.NIGHT;
            case DAY_FIRST -> s == 0 || t < s ? ShiftKind.DAY : ShiftKind.NIGHT;
            case NIGHT_FIRST -> {
                if (s == 0 || t < s) {
                    yield ShiftKind.NIGHT;
                }
                yield template.restAfterNights() && t == s ? ShiftKind.REST : ShiftKind.DAY;
            }
        };
    }

    /**
     * Норма сотрудника за сутки (минуты): дневная смена — целиком; ночная делится по полуночи — часть до полуночи
     * к дню начала, часть после — к следующему дню (раздел 19.15).
     */
    public int plannedMinutes(UUID employeeId, LocalDate date) {
        int minutes = 0;
        ShiftKind today = kind(employeeId, date);
        if (today == ShiftKind.DAY) {
            minutes += template.day().paidMinutesBeforeMidnight();
        } else if (today == ShiftKind.NIGHT) {
            minutes += template.night().paidMinutesBeforeMidnight();
        }
        ShiftKind yesterday = kind(employeeId, date.minusDays(1));
        if (yesterday == ShiftKind.NIGHT) {
            minutes += template.night().paidMinutesAfterMidnight();
        } else if (yesterday == ShiftKind.DAY) {
            minutes += template.day().paidMinutesAfterMidnight();
        }
        return minutes;
    }

    /** Покрытие смен по дням: сколько человек выходят днём и ночью. */
    public List<Coverage> coverage(LocalDate from, LocalDate to) {
        List<Coverage> result = new ArrayList<>();
        for (LocalDate d = from; !d.isAfter(to); d = d.plusDays(1)) {
            int day = 0;
            int night = 0;
            for (UUID id : members.keySet()) {
                ShiftKind k = kind(id, d);
                if (k == ShiftKind.DAY) {
                    day++;
                } else if (k == ShiftKind.NIGHT) {
                    night++;
                }
            }
            boolean workingDay = template.mode() == ScheduleMode.ROTATION || calendar.isWorking(d);
            result.add(new Coverage(d, day, night, workingDay ? requiredDay : 0,
                    template.hasNights() ? requiredNight : 0));
        }
        return result;
    }

    // ---------- Распределение и смещение ----------

    /**
     * Сколько человек одновременно днём и ночью в бригаде из size человек: поровну, лишний — по приоритету.
     * Без ночных смен — все днём.
     */
    public static int[] split(ScheduleTemplate t, int size) {
        if (!t.hasNights()) {
            return new int[] {size, 0};
        }
        int small = size / 2;
        int big = size - small;
        return t.dayPriority() ? new int[] {big, small} : new int[] {small, big};
    }

    /**
     * Распределение сотрудников (в заданном порядке): при сменном графике первая половина — бригада А, вторая —
     * бригада Б, i-й из А — сменщик i-го из Б. Внутри бригады при ночных сменах — подгруппы дня и ночи по
     * приоритету; если дневные и ночные меняются местами, лишний человек остаётся всю вахту в приоритетной смене,
     * чтобы приоритет сохранялся и после смены мест.
     */
    public static List<Member> assign(ScheduleTemplate t, List<UUID> ids, Map<UUID, String> names) {
        List<Member> result = new ArrayList<>();
        if (t.mode() == ScheduleMode.FIVE_TWO) {
            ids.forEach(id -> result.add(new Member(id, names.get(id), Crew.A, Track.FIXED_DAY, 0, null)));
            return result;
        }
        int half = (ids.size() + 1) / 2;
        List<UUID> a = ids.subList(0, half);
        List<UUID> b = ids.subList(half, ids.size());
        result.addAll(crew(t, Crew.A, a, b, names));
        result.addAll(crew(t, Crew.B, b, a, names));
        return result;
    }

    private static List<Member> crew(ScheduleTemplate t, Crew crew, List<UUID> own, List<UUID> other,
                                     Map<UUID, String> names) {
        List<Member> list = new ArrayList<>();
        int[] split = split(t, own.size());
        int pairs = Math.min(split[0], split[1]);
        for (int i = 0; i < own.size(); i++) {
            Track track;
            if (!t.hasNights()) {
                track = Track.FIXED_DAY;
            } else if (t.switchAfterDays() == 0) {
                track = i < split[0] ? Track.FIXED_DAY : Track.FIXED_NIGHT;
            } else if (i < pairs) {
                track = Track.DAY_FIRST;
            } else if (i < 2 * pairs) {
                track = Track.NIGHT_FIRST;
            } else {
                track = t.dayPriority() ? Track.FIXED_DAY : Track.FIXED_NIGHT;
            }
            UUID partner = i < other.size() ? other.get(i) : null;
            list.add(new Member(own.get(i), names.get(own.get(i)), crew, track, 0, partner));
        }
        return list;
    }

    /**
     * Смещение заезда: выбранные сотрудники и их сменщики сдвигаются на одинаковое число дней, чтобы в смене
     * оставалось нужное число людей (раздел 19.15).
     */
    public static List<Member> shiftArrival(List<Member> members, Set<UUID> ids, int days) {
        Set<UUID> affected = new HashSet<>(ids);
        for (Member m : members) {
            if (ids.contains(m.employeeId()) && m.partnerId() != null) {
                affected.add(m.partnerId());
            }
        }
        return members.stream().map(m -> affected.contains(m.employeeId()) ? m.withOffset(m.offsetDays() + days) : m)
                .toList();
    }
}
