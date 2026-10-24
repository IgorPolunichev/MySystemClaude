package com.company.acts.analytics.application.view;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/** Дашборд за период: инженеры и отделы — факт, норма, процент; пороги цветов. */
public record DashboardView(LocalDate from, LocalDate to, List<EngineerRow> engineers, List<DepartmentRow> departments,
                            Thresholds thresholds, Scope scope) {

    public record EngineerRow(UUID engineerId, String fullName, UUID departmentId, String departmentName,
                              BigDecimal normHours, BigDecimal factHours, int percent, int acts, int machines) {
    }

    /** normComputed — нормы отдела нет, показана сумма норм его инженеров. */
    public record DepartmentRow(UUID departmentId, String name, BigDecimal normHours, boolean normComputed,
                                BigDecimal factHours, int percent) {
    }

    /** До red — красный, до yellow — жёлтый, от yellow — зелёный. */
    public record Thresholds(int red, int yellow) {
    }

    /** Что видит пользователь: SELF — только себя, DEPARTMENT — свой отдел, ALL — всех. */
    public enum Scope {
        SELF,
        DEPARTMENT,
        ALL
    }
}
