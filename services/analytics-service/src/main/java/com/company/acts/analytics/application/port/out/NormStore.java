package com.company.acts.analytics.application.port.out;

import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.Map;
import java.util.UUID;

/** Месячные нормы часов инженеров и отделов. */
public interface NormStore {

    /** id → (месяц → норма) за интервал месяцев включительно. */
    Map<UUID, Map<YearMonth, BigDecimal>> engineerNorms(YearMonth from, YearMonth to);

    Map<UUID, Map<YearMonth, BigDecimal>> departmentNorms(YearMonth from, YearMonth to);

    /** hours = null — удалить норму. */
    void setEngineerNorm(UUID engineerId, YearMonth month, BigDecimal hours, String user);

    void setDepartmentNorm(UUID departmentId, YearMonth month, BigDecimal hours, String user);

    /** Скопировать нормы месяца в другой месяц, не трогая уже заданные; возвращает число скопированных. */
    int copyMonth(YearMonth from, YearMonth to, String user);
}
