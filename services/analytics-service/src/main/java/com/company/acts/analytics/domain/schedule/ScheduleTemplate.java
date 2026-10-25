package com.company.acts.analytics.domain.schedule;

/**
 * Конструктор графика (раздел 19.15 SPEC.md).
 *
 * @param mode               5/2 по производственному календарю или сменный/вахтовый N/M
 * @param workDays           N — дней на смене (вахте) подряд; для 5/2 не используется
 * @param offDays            M — дней отдыха (межвахты); для 5/2 не используется
 * @param day                время дневной смены
 * @param night              время ночной смены; null — ночных смен нет
 * @param switchAfterDays    через сколько дней вахты дневные и ночные меняются местами; 0 — не меняются
 *                           (часть бригады всю вахту днём, часть ночью)
 * @param restAfterNights    отсыпной день при переходе с ночных смен на дневные
 * @param overlapDays        пересекающиеся дни: приезжающая бригада заезжает раньше на столько дней
 * @param dayPriority        при нечётном числе людей в бригаде лишний — в дневную смену (иначе в ночную)
 */
public record ScheduleTemplate(String name, ScheduleMode mode, int workDays, int offDays, ShiftTime day, ShiftTime night,
                               int switchAfterDays, boolean restAfterNights, int overlapDays, boolean dayPriority) {

    public ScheduleTemplate {
        if (mode == null || day == null) {
            throw new IllegalArgumentException("Укажите режим и время дневной смены");
        }
        if (mode == ScheduleMode.ROTATION) {
            if (workDays < 1 || offDays < 1 || workDays > 120 || offDays > 120) {
                throw new IllegalArgumentException("Дней на смене и дней отдыха — от 1 до 120");
            }
            if (overlapDays < 0 || overlapDays >= workDays) {
                throw new IllegalArgumentException("Пересекающихся дней должно быть меньше, чем дней на смене");
            }
            if (night != null && (switchAfterDays < 0 || switchAfterDays >= workDays)) {
                throw new IllegalArgumentException("Смена дневных и ночных — в пределах дней на смене");
            }
        }
        if (mode == ScheduleMode.FIVE_TWO && night != null) {
            throw new IllegalArgumentException("График 5/2 — только дневные смены");
        }
    }

    public boolean hasNights() {
        return night != null;
    }

    public int cycleDays() {
        return workDays + offDays;
    }

    /**
     * Предупреждение о разрыве между бригадами: при двух бригадах смена непрерывна, если дней на смене не меньше,
     * чем дней отдыха плюс пересекающиеся дни. null — разрыва нет.
     */
    public String continuityWarning() {
        if (mode != ScheduleMode.ROTATION || workDays >= offDays + overlapDays) {
            return null;
        }
        int gap = offDays + overlapDays - workDays;
        return "Между уходом одной бригады и заездом другой " + gap + " дн. без смены: дней на смене должно быть"
                + " не меньше, чем дней отдыха плюс пересекающиеся дни (" + (offDays + overlapDays) + ")";
    }
}
