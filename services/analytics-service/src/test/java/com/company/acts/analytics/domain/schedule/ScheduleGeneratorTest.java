package com.company.acts.analytics.domain.schedule;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.company.acts.analytics.domain.WorkCalendar;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** Построение графиков на примерах заказчика (раздел 19.15 SPEC.md). */
class ScheduleGeneratorTest {

    static final ShiftTime DAY = new ShiftTime(LocalTime.of(8, 0), LocalTime.of(20, 0), 60);
    static final ShiftTime NIGHT = new ShiftTime(LocalTime.of(20, 0), LocalTime.of(8, 0), 60);
    static final LocalDate START = LocalDate.of(2026, 1, 1);
    static final WorkCalendar CALENDAR = new WorkCalendar(Map.of(LocalDate.of(2026, 1, 2), false));

    final List<UUID> ids = new ArrayList<>();
    final Map<UUID, String> names = new HashMap<>();

    ScheduleGeneratorTest() {
        for (int i = 1; i <= 12; i++) {
            UUID id = UUID.randomUUID();
            ids.add(id);
            names.put(id, "Сотрудник " + i);
        }
    }

    static long shortages(ScheduleGenerator g, LocalDate from, LocalDate to) {
        return g.coverage(from, to).stream().filter(ScheduleGenerator.Coverage::shortage).count();
    }

    @Test
    void nightShiftIsSplitAtMidnight() {
        assertThat(NIGHT.paidMinutes()).isEqualTo(660);
        assertThat(NIGHT.paidMinutesBeforeMidnight()).isEqualTo(220);
        assertThat(NIGHT.paidMinutesAfterMidnight()).isEqualTo(440);
        assertThat(DAY.paidMinutesBeforeMidnight()).isEqualTo(660);
    }

    @Test
    void twelvePeopleOnRotation45x45AlwaysThreeByDayAndThreeByNight() {
        var t = new ScheduleTemplate("Вахта 45/45", ScheduleMode.ROTATION, 45, 45, DAY, NIGHT, 0, false, 0, true);
        var members = ScheduleGenerator.assign(t, ids, names);
        assertThat(members).filteredOn(m -> m.crew() == Crew.A).hasSize(6);
        assertThat(members).allSatisfy(m -> assertThat(m.partnerId()).isNotNull());
        var g = new ScheduleGenerator(t, START, members, Map.of(), CALENDAR, 3, 3);
        assertThat(g.coverage(START, START).get(0)).satisfies(c -> {
            assertThat(c.day()).isEqualTo(3);
            assertThat(c.night()).isEqualTo(3);
        });
        assertThat(shortages(g, START, LocalDate.of(2026, 12, 31))).isZero();

        // ночная смена делится по полуночи: 01.01 — 3 ч 40 мин, 02.01 — 7 ч 20 мин + 3 ч 40 мин следующей ночи
        UUID nightA = members.stream().filter(m -> m.crew() == Crew.A && m.track() == Track.FIXED_NIGHT).findFirst()
                .orElseThrow().employeeId();
        assertThat(g.plannedMinutes(nightA, START)).isEqualTo(220);
        assertThat(g.plannedMinutes(nightA, START.plusDays(1))).isEqualTo(660);
        assertThat(g.kind(nightA, START.plusDays(45))).isEqualTo(ShiftKind.OFF);
        assertThat(g.plannedMinutes(nightA, START.plusDays(45))).isEqualTo(440);
    }

    @Test
    void shiftingArrivalMovesPartnersSoCoverageStays() {
        var t = new ScheduleTemplate("Вахта 45/45", ScheduleMode.ROTATION, 45, 45, DAY, NIGHT, 0, false, 0, true);
        var members = ScheduleGenerator.assign(t, ids, names);
        var shifted = ScheduleGenerator.shiftArrival(members, Set.of(ids.get(0), ids.get(1), ids.get(2)), 22);
        assertThat(shifted).filteredOn(m -> m.offsetDays() == 22).hasSize(6);
        var g = new ScheduleGenerator(t, START, shifted, Map.of(), CALENDAR, 3, 3);
        assertThat(shortages(g, START, LocalDate.of(2026, 12, 31))).isZero();
    }

    @Test
    void oddCrewFollowsPriorityAndManualMarksCreateShortage() {
        var day = new ScheduleTemplate("x", ScheduleMode.ROTATION, 45, 45, DAY, NIGHT, 0, false, 0, true);
        var night = new ScheduleTemplate("x", ScheduleMode.ROTATION, 45, 45, DAY, NIGHT, 0, false, 0, false);
        assertThat(ScheduleGenerator.split(day, 7)).containsExactly(4, 3);
        assertThat(ScheduleGenerator.split(night, 7)).containsExactly(3, 4);

        var members = ScheduleGenerator.assign(day, ids, names);
        var g = new ScheduleGenerator(day, START, members,
                Map.of(ids.get(0), Map.of(START.plusDays(4), ShiftKind.VACATION)), CALENDAR, 3, 3);
        assertThat(g.coverage(START.plusDays(4), START.plusDays(4)).get(0).shortage()).isTrue();
    }

    @Test
    void swappingDayAndNightWithRestDayLeavesOneDayWithoutDayShift() {
        var t = new ScheduleTemplate("Со сменой мест", ScheduleMode.ROTATION, 45, 45, DAY, NIGHT, 22, true, 0, true);
        var members = ScheduleGenerator.assign(t, ids, names);
        UUID nightFirst = members.stream().filter(m -> m.track() == Track.NIGHT_FIRST).findFirst().orElseThrow()
                .employeeId();
        var g = new ScheduleGenerator(t, START, members, Map.of(), CALENDAR, 3, 3);
        assertThat(g.kind(nightFirst, START.plusDays(21))).isEqualTo(ShiftKind.NIGHT);
        assertThat(g.kind(nightFirst, START.plusDays(22))).isEqualTo(ShiftKind.REST);
        assertThat(g.kind(nightFirst, START.plusDays(23))).isEqualTo(ShiftKind.DAY);
        // в день перехода дневную смену бригады А закрыть некем — график показывает нехватку
        assertThat(g.coverage(START.plusDays(22), START.plusDays(22)).get(0).day()).isZero();
    }

    @Test
    void twoByTwoAndFiveByTwoAndValidation() {
        var t22 = new ScheduleTemplate("2/2", ScheduleMode.ROTATION, 2, 2, DAY, null, 0, false, 0, true);
        var g = new ScheduleGenerator(t22, START, ScheduleGenerator.assign(t22, ids.subList(0, 4), names), Map.of(),
                CALENDAR, 2, 0);
        List<String> letters = new ArrayList<>();
        for (int i = 0; i < 8; i++) {
            letters.add(g.kind(ids.get(0), START.plusDays(i)).letter());
        }
        assertThat(letters).containsExactly("Д", "Д", "В", "В", "Д", "Д", "В", "В");
        assertThat(shortages(g, START, LocalDate.of(2026, 12, 31))).isZero();

        var t52 = new ScheduleTemplate("5/2", ScheduleMode.FIVE_TWO, 5, 2, DAY, null, 0, false, 0, true);
        var g52 = new ScheduleGenerator(t52, START, ScheduleGenerator.assign(t52, ids.subList(0, 2), names), Map.of(),
                CALENDAR, 2, 0);
        assertThat(g52.kind(ids.get(0), LocalDate.of(2026, 1, 2))).isEqualTo(ShiftKind.OFF); // праздник календаря
        assertThat(g52.kind(ids.get(0), LocalDate.of(2026, 1, 5))).isEqualTo(ShiftKind.DAY);
        assertThat(g52.kind(ids.get(0), LocalDate.of(2026, 1, 3))).isEqualTo(ShiftKind.OFF);

        assertThat(new ScheduleTemplate("x", ScheduleMode.ROTATION, 45, 45, DAY, NIGHT, 0, false, 1, true)
                .continuityWarning()).contains("46");
        assertThatThrownBy(() -> new ScheduleTemplate("x", ScheduleMode.FIVE_TWO, 5, 2, DAY, NIGHT, 0, false, 0, true))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new ShiftTime(LocalTime.of(8, 0), LocalTime.of(9, 0), 60))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
