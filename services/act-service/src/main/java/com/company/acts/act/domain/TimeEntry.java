package com.company.acts.act.domain;

import com.company.acts.common.core.BusinessRuleException;
import com.company.acts.common.core.Strings;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Objects;
import java.util.UUID;

/**
 * Время одного инженера за один день по акту. Продуктивные часы — шаг 0,25 ч, от 0 до 24;
 * время в пути учитывается отдельно и в продуктивные не входит.
 */
public record TimeEntry(UUID id, UUID actId, UUID engineerId, LocalDate workDate, BigDecimal hours,
                        BigDecimal travelHours, String comment) {

    private static final BigDecimal MAX = BigDecimal.valueOf(24);
    private static final BigDecimal STEP = new BigDecimal("0.25");

    public TimeEntry {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(actId, "actId");
        Objects.requireNonNull(engineerId, "engineerId");
        if (workDate == null) {
            throw new BusinessRuleException("workDate", "Укажите дату");
        }
        hours = hours == null ? BigDecimal.ZERO : hours;
        comment = Strings.trimToNull(comment);
        validate("hours", hours, "Продуктивные часы");
        if (travelHours != null) {
            validate("travelHours", travelHours, "Время в пути");
        }
        if (hours.signum() == 0 && (travelHours == null || travelHours.signum() == 0)) {
            throw new BusinessRuleException("hours", "Укажите часы работы или время в пути");
        }
    }

    private static void validate(String field, BigDecimal value, String label) {
        if (value.signum() < 0 || value.compareTo(MAX) > 0) {
            throw new BusinessRuleException(field, label + ": от 0 до 24 часов");
        }
        if (value.remainder(STEP).signum() != 0) {
            throw new BusinessRuleException(field, label + ": шаг 0,25 ч (15 минут)");
        }
    }
}
