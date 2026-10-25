package com.company.acts.analytics.application.view;

import com.company.acts.analytics.domain.schedule.Crew;
import com.company.acts.analytics.domain.schedule.ScheduleMode;
import com.company.acts.analytics.domain.schedule.ShiftKind;
import com.company.acts.analytics.domain.schedule.Track;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;

/** Представления графиков для API. */
public final class ScheduleViews {

    private ScheduleViews() {
    }

    public record TemplateView(UUID id, String name, ScheduleMode mode, int workDays, int offDays, LocalTime dayStart,
                               LocalTime dayEnd, int dayBreakMinutes, LocalTime nightStart, LocalTime nightEnd,
                               Integer nightBreakMinutes, int switchAfterDays, boolean restAfterNights, int overlapDays,
                               boolean dayPriority, BigDecimal dayHours, BigDecimal nightHours, String warning,
                               boolean inUse) {
    }

    public record MemberView(UUID employeeId, String name, Crew crew, Track track, int offsetDays, UUID partnerId,
                             String partnerName) {
    }

    public record GroupView(UUID id, String name, UUID departmentId, UUID siteId, String siteName, UUID templateId,
                            String templateName, LocalDate startDate, int requiredDay, int requiredNight,
                            List<MemberView> members, Instant approvedAt, String approvedBy, long version,
                            boolean canEdit, String warning) {
    }

    /** Сутки сотрудника: смена, буква табеля, норма (часы) и ручная ли это отметка. */
    public record DayCell(LocalDate date, ShiftKind kind, String letter, BigDecimal hours, boolean manual) {
    }

    public record EmployeeRow(UUID employeeId, String name, Crew crew, Track track, List<DayCell> days,
                              BigDecimal totalHours, int shifts) {
    }

    public record CoverageCell(LocalDate date, int day, int night, int requiredDay, int requiredNight,
                               boolean shortage) {
    }

    public record Grid(UUID groupId, String groupName, LocalDate from, LocalDate to, List<EmployeeRow> employees,
                       List<CoverageCell> coverage, int shortageDays) {
    }
}
