package com.company.acts.analytics.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.Map;
import org.junit.jupiter.api.Test;

class NormCalculatorTest {

    private final WorkCalendar calendar = new WorkCalendar(Map.of(LocalDate.of(2026, 11, 4), false));
    private final Map<YearMonth, BigDecimal> norms = Map.of(YearMonth.of(2026, 10), new BigDecimal("168"),
            YearMonth.of(2026, 11), new BigDecimal("160"));

    @Test
    void fullMonthsSumUpAndPartialMonthsAreProportionalToWorkingDays() {
        assertThat(calendar.workingDays(LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 31))).isEqualTo(22);
        assertThat(calendar.workingDays(LocalDate.of(2026, 11, 1), LocalDate.of(2026, 11, 30))).isEqualTo(20);
        assertThat(NormCalculator.forPeriod(norms, LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 31), calendar))
                .isEqualByComparingTo("168");
        // 12 из 22 рабочих дней октября + 9 из 20 ноября (4 ноября — праздник)
        assertThat(NormCalculator.forPeriod(norms, LocalDate.of(2026, 10, 15), LocalDate.of(2026, 11, 14), calendar))
                .isEqualByComparingTo("163.64");
        assertThat(NormCalculator.forPeriod(norms, LocalDate.of(2026, 10, 1), LocalDate.of(2026, 12, 31), calendar))
                .isEqualByComparingTo("328");
        assertThat(NormCalculator.forPeriod(Map.of(), LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 31), calendar))
                .isNull();
    }

    @Test
    void percent() {
        assertThat(NormCalculator.percent(new BigDecimal("150"), new BigDecimal("168"))).isEqualTo(89);
        assertThat(NormCalculator.percent(BigDecimal.TEN, null)).isZero();
    }
}
