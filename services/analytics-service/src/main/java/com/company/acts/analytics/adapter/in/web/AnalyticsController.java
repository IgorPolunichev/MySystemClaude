package com.company.acts.analytics.adapter.in.web;

import com.company.acts.analytics.application.CalendarService;
import com.company.acts.analytics.application.DashboardService;
import com.company.acts.analytics.application.NormService;
import com.company.acts.analytics.application.RebuildService;
import com.company.acts.analytics.application.view.DashboardView;
import com.company.acts.analytics.application.view.NormMonthView;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.UUID;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Дашборд продуктивных часов, нормы, производственный календарь, пересборка (раздел 9 SPEC.md). */
@RestController
class AnalyticsController {

    record HoursRequest(BigDecimal hours) {
    }

    record CalendarRequest(Boolean working, @Size(max = 200) String comment) {
    }

    record CountResponse(int count) {
    }

    private final DashboardService dashboard;
    private final NormService norms;
    private final CalendarService calendar;
    private final RebuildService rebuild;

    AnalyticsController(DashboardService dashboard, NormService norms, CalendarService calendar, RebuildService rebuild) {
        this.dashboard = dashboard;
        this.norms = norms;
        this.calendar = calendar;
        this.rebuild = rebuild;
    }

    @GetMapping("/api/v1/analytics/dashboard")
    DashboardView dashboard(@RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
                            @RequestParam(required = false) UUID departmentId,
                            @RequestParam(required = false) List<UUID> engineerId,
                            @RequestParam(required = false) UUID organizationId) {
        return dashboard.dashboard(from, to, departmentId, engineerId, organizationId);
    }

    @GetMapping("/api/v1/analytics/dashboard/export")
    ResponseEntity<byte[]> export(@RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                  @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
                                  @RequestParam(required = false) UUID departmentId,
                                  @RequestParam(required = false) List<UUID> engineerId,
                                  @RequestParam(required = false) UUID organizationId) {
        DashboardView view = dashboard.dashboard(from, to, departmentId, engineerId, organizationId);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                        .filename("Продуктивные часы.xlsx", StandardCharsets.UTF_8).build().toString())
                .body(DashboardWorkbook.write(view));
    }

    @GetMapping("/api/v1/analytics/series")
    List<DashboardService.Point> series(@RequestParam UUID engineerId,
                                        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
                                        @RequestParam(defaultValue = "day") String granularity) {
        return dashboard.series(engineerId, from, to, "week".equals(granularity));
    }

    @PostMapping("/api/v1/analytics/rebuild")
    RebuildService.Result rebuild() {
        return rebuild.rebuild();
    }

    @GetMapping("/api/v1/norms")
    NormMonthView norms(@RequestParam String month) {
        return norms.month(month(month));
    }

    @PutMapping("/api/v1/norms/engineers/{engineerId}/{month}")
    void engineerNorm(@PathVariable UUID engineerId, @PathVariable String month,
                      @RequestBody HoursRequest request) {
        norms.setEngineerNorm(engineerId, month(month), request.hours());
    }

    @PutMapping("/api/v1/norms/departments/{departmentId}/{month}")
    void departmentNorm(@PathVariable UUID departmentId,
                        @PathVariable String month,
                        @RequestBody HoursRequest request) {
        norms.setDepartmentNorm(departmentId, month(month), request.hours());
    }

    @PostMapping("/api/v1/norms/departments/{departmentId}/{month}/engineers")
    CountResponse allEngineers(@PathVariable UUID departmentId,
                               @PathVariable String month,
                               @RequestBody HoursRequest request) {
        return new CountResponse(norms.setForAllEngineers(departmentId, month(month), request.hours()));
    }

    @PostMapping("/api/v1/norms/{month}/copy-previous")
    CountResponse copyPrevious(@PathVariable String month) {
        return new CountResponse(norms.copyFromPrevious(month(month)));
    }

    @GetMapping("/api/v1/calendar/{year}")
    CalendarService.YearView calendar(@PathVariable int year) {
        return calendar.year(year);
    }

    @PutMapping("/api/v1/calendar/days/{date}")
    void calendarDay(@PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
                     @RequestBody CalendarRequest request) {
        calendar.set(date, request.working(), request.comment());
    }

    /** Месяц в формате ГГГГ-ММ, например 2026-10. */
    private static YearMonth month(String value) {
        try {
            return YearMonth.parse(value);
        } catch (java.time.format.DateTimeParseException e) {
            throw new com.company.acts.common.core.BusinessRuleException("month", "Месяц — в формате ГГГГ-ММ, например 2026-10");
        }
    }
}
