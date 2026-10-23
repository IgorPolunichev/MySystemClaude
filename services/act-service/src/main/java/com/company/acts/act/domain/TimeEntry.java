package com.company.acts.act.domain;

import com.company.acts.common.core.BusinessRuleException;
import com.company.acts.common.core.Strings;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Время одного инженера за один день по акту (раздел 6.4 SPEC.md). Продуктивное время не вводится, а
 * считается: интервал работы (начало — окончание) минус перерывы. Пример: 10:00–13:00, обед 11:00–12:00 → 2 ч.
 *
 * <ul>
 *   <li>Если окончание не позже начала, работа заканчивается на следующий день (ночная смена); часы
 *       относятся к дате начала. Окончание, равное началу, — ровно 24 часа.</li>
 *   <li>Перерывы лежат внутри интервала и не пересекаются.</li>
 *   <li>Точность — минута; часы = минуты / 60 с округлением до сотых только для отображения.</li>
 *   <li>Время в пути — отдельное число часов, в продуктивное время не входит. Запись может состоять только
 *       из времени в пути — тогда интервала нет.</li>
 * </ul>
 */
public record TimeEntry(UUID id, UUID actId, UUID engineerId, LocalDate workDate, LocalTime startTime,
                        LocalTime endTime, List<WorkBreak> breaks, BigDecimal travelHours, String comment) {

    public static final int DAY_MINUTES = 24 * 60;
    private static final BigDecimal MAX_TRAVEL = BigDecimal.valueOf(24);
    private static final BigDecimal TRAVEL_STEP = new BigDecimal("0.25");

    public TimeEntry {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(actId, "actId");
        Objects.requireNonNull(engineerId, "engineerId");
        if (workDate == null) {
            throw new BusinessRuleException("workDate", "Укажите дату");
        }
        breaks = breaks == null ? List.of() : List.copyOf(breaks);
        comment = Strings.trimToNull(comment);
        startTime = truncate(startTime);
        endTime = truncate(endTime);

        if ((startTime == null) != (endTime == null)) {
            throw new BusinessRuleException(startTime == null ? "startTime" : "endTime",
                    "Укажите и начало, и окончание работы");
        }
        if (travelHours != null) {
            if (travelHours.signum() < 0 || travelHours.compareTo(MAX_TRAVEL) > 0) {
                throw new BusinessRuleException("travelHours", "Время в пути: от 0 до 24 часов");
            }
            if (travelHours.remainder(TRAVEL_STEP).signum() != 0) {
                throw new BusinessRuleException("travelHours", "Время в пути: шаг 0,25 ч (15 минут)");
            }
        }
        if (startTime == null) {
            if (!breaks.isEmpty()) {
                throw new BusinessRuleException("breaks", "Перерывы указываются только вместе с интервалом работы");
            }
            if (travelHours == null || travelHours.signum() == 0) {
                throw new BusinessRuleException("startTime", "Укажите начало и окончание работы или время в пути");
            }
        } else {
            validateBreaks(startTime, span(startTime, endTime), breaks);
        }
    }

    /** Продуктивные минуты: длительность интервала минус перерывы. */
    public int workMinutes() {
        if (startTime == null) {
            return 0;
        }
        int total = span(startTime, endTime);
        for (WorkBreak b : breaks) {
            total -= offset(startTime, b.end()) - offset(startTime, b.start());
        }
        return total;
    }

    /** Продуктивные часы, округлённые до сотых (2 ч 20 мин → 2,33). */
    public BigDecimal hours() {
        return hoursOf(workMinutes());
    }

    public static BigDecimal hoursOf(long minutes) {
        return BigDecimal.valueOf(minutes).divide(BigDecimal.valueOf(60), 2, RoundingMode.HALF_UP);
    }

    /** Длительность интервала в минутах; окончание не позже начала — переход через полночь. */
    static int span(LocalTime start, LocalTime end) {
        int diff = Math.floorMod(minuteOfDay(end) - minuteOfDay(start), DAY_MINUTES);
        return diff == 0 ? DAY_MINUTES : diff;
    }

    /** Минут от начала работы до момента t (с учётом перехода через полночь). */
    private static int offset(LocalTime start, LocalTime t) {
        return Math.floorMod(minuteOfDay(t) - minuteOfDay(start), DAY_MINUTES);
    }

    private static void validateBreaks(LocalTime start, int span, List<WorkBreak> breaks) {
        int[][] ranges = new int[breaks.size()][];
        for (int i = 0; i < breaks.size(); i++) {
            WorkBreak b = breaks.get(i);
            if (b == null || b.start() == null || b.end() == null) {
                throw new BusinessRuleException("breaks", "Укажите начало и окончание каждого перерыва");
            }
            int from = offset(start, truncate(b.start()));
            int to = offset(start, truncate(b.end()));
            if (to == 0 && from > 0) {
                to = span == DAY_MINUTES ? DAY_MINUTES : 0;
            }
            if (to <= from) {
                throw new BusinessRuleException("breaks", "Перерыв " + text(b) + " должен заканчиваться позже, чем начинается");
            }
            if (to > span) {
                throw new BusinessRuleException("breaks", "Перерыв " + text(b) + " выходит за интервал работы");
            }
            ranges[i] = new int[] {from, to};
        }
        java.util.Arrays.sort(ranges, Comparator.comparingInt(r -> r[0]));
        int breakMinutes = 0;
        for (int i = 0; i < ranges.length; i++) {
            if (i > 0 && ranges[i][0] < ranges[i - 1][1]) {
                throw new BusinessRuleException("breaks", "Перерывы не должны пересекаться");
            }
            breakMinutes += ranges[i][1] - ranges[i][0];
        }
        if (breakMinutes >= span) {
            throw new BusinessRuleException("breaks", "Перерывы занимают всё время работы");
        }
    }

    private static String text(WorkBreak b) {
        return b.start().toString() + "–" + b.end().toString();
    }

    private static int minuteOfDay(LocalTime t) {
        return t.getHour() * 60 + t.getMinute();
    }

    private static LocalTime truncate(LocalTime t) {
        return t == null ? null : t.withSecond(0).withNano(0);
    }
}
