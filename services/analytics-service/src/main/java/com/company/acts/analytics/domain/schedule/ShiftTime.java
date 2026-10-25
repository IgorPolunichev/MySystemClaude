package com.company.acts.analytics.domain.schedule;

import java.time.Duration;
import java.time.LocalTime;

/**
 * Время смены: начало, окончание, перерыв. Окончание не позже начала — смена заканчивается на следующие сутки.
 * Оплачиваемое время = продолжительность − перерыв.
 */
public record ShiftTime(LocalTime start, LocalTime end, int breakMinutes) {

    public ShiftTime {
        if (start == null || end == null) {
            throw new IllegalArgumentException("Укажите начало и окончание смены");
        }
        if (breakMinutes < 0 || breakMinutes >= spanMinutes(start, end)) {
            throw new IllegalArgumentException("Перерыв должен быть меньше продолжительности смены");
        }
    }

    public boolean crossesMidnight() {
        return !end.isAfter(start);
    }

    public int spanMinutes() {
        return spanMinutes(start, end);
    }

    /** Оплачиваемые минуты смены — норма дня. */
    public int paidMinutes() {
        return spanMinutes() - breakMinutes;
    }

    /** Минуты смены до полуночи (для смены через полночь), пропорционально с учётом перерыва. */
    public int paidMinutesBeforeMidnight() {
        if (!crossesMidnight()) {
            return paidMinutes();
        }
        int before = (int) Duration.between(start, LocalTime.MAX).toMinutes() + 1;
        return Math.round((float) paidMinutes() * before / spanMinutes());
    }

    public int paidMinutesAfterMidnight() {
        return paidMinutes() - paidMinutesBeforeMidnight();
    }

    private static int spanMinutes(LocalTime start, LocalTime end) {
        int minutes = (int) Duration.between(start, end).toMinutes();
        return minutes <= 0 ? minutes + 24 * 60 : minutes;
    }
}
