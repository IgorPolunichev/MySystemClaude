package com.company.acts.analytics.application.view;

import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.List;
import java.util.UUID;

/** Нормы на месяц: по инженерам и по отделам (заданная или вычисленная как сумма). */
public record NormMonthView(YearMonth month, int workingDays, List<EngineerNorm> engineers,
                            List<DepartmentNorm> departments, boolean canEdit) {

    public record EngineerNorm(UUID engineerId, String fullName, UUID departmentId, String departmentName,
                               BigDecimal hours) {
    }

    public record DepartmentNorm(UUID departmentId, String name, BigDecimal hours, BigDecimal computedHours) {
    }
}
