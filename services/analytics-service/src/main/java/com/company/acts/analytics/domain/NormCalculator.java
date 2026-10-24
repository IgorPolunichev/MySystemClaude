package com.company.acts.analytics.domain;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.Map;

/**
 * Норма на произвольный период = сумма месячных норм; если период захватывает часть месяца — норма месяца
 * пропорционально рабочим дням этой части (раздел 9 SPEC.md).
 */
public final class NormCalculator {

    private NormCalculator() {
    }

    /** null — ни для одного месяца периода норма не задана. */
    public static BigDecimal forPeriod(Map<YearMonth, BigDecimal> monthly, LocalDate from, LocalDate to,
                                       WorkCalendar calendar) {
        BigDecimal total = null;
        for (YearMonth month = YearMonth.from(from); !month.isAfter(YearMonth.from(to)); month = month.plusMonths(1)) {
            BigDecimal norm = monthly.get(month);
            if (norm == null) {
                continue;
            }
            LocalDate start = month.atDay(1);
            LocalDate end = month.atEndOfMonth();
            LocalDate partStart = from.isAfter(start) ? from : start;
            LocalDate partEnd = to.isBefore(end) ? to : end;
            BigDecimal part;
            if (partStart.equals(start) && partEnd.equals(end)) {
                part = norm;
            } else {
                int monthDays = calendar.workingDays(start, end);
                int partDays = calendar.workingDays(partStart, partEnd);
                part = monthDays == 0 ? BigDecimal.ZERO
                        : norm.multiply(BigDecimal.valueOf(partDays)).divide(BigDecimal.valueOf(monthDays), 2,
                        RoundingMode.HALF_UP);
            }
            total = total == null ? part : total.add(part);
        }
        return total;
    }

    /** Процент выполнения (0 — если норма не задана или равна нулю). */
    public static int percent(BigDecimal fact, BigDecimal norm) {
        if (norm == null || norm.signum() == 0) {
            return 0;
        }
        return fact.multiply(BigDecimal.valueOf(100)).divide(norm, 0, RoundingMode.HALF_UP).intValue();
    }
}
