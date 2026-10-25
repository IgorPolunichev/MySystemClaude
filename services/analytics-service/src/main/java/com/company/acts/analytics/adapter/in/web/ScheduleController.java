package com.company.acts.analytics.adapter.in.web;

import com.company.acts.analytics.application.ScheduleService;
import com.company.acts.analytics.application.view.ScheduleViews.Grid;
import com.company.acts.analytics.application.view.ScheduleViews.GroupView;
import com.company.acts.analytics.application.view.ScheduleViews.TemplateView;
import com.company.acts.analytics.domain.schedule.ShiftKind;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.util.HashSet;
import java.util.List;
import java.util.UUID;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Рабочие графики (раздел 19.15 SPEC.md). */
@RestController
class ScheduleController {

    record ShiftRequest(List<UUID> employeeIds, int days) {
    }

    record MarkRequest(UUID employeeId, LocalDate from, LocalDate to, ShiftKind kind, @Size(max = 300) String comment) {
    }

    private final ScheduleService schedules;

    ScheduleController(ScheduleService schedules) {
        this.schedules = schedules;
    }

    @GetMapping("/api/v1/schedules/templates")
    List<TemplateView> templates() {
        return schedules.templates();
    }

    @PostMapping("/api/v1/schedules/templates")
    @ResponseStatus(HttpStatus.CREATED)
    TemplateView createTemplate(@RequestBody ScheduleService.TemplateCommand c) {
        return schedules.saveTemplate(null, c);
    }

    @PutMapping("/api/v1/schedules/templates/{id}")
    TemplateView updateTemplate(@PathVariable UUID id, @RequestBody ScheduleService.TemplateCommand c) {
        return schedules.saveTemplate(id, c);
    }

    @DeleteMapping("/api/v1/schedules/templates/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void deleteTemplate(@PathVariable UUID id) {
        schedules.deleteTemplate(id);
    }

    @GetMapping("/api/v1/schedules/groups")
    List<GroupView> groups() {
        return schedules.groups();
    }

    @GetMapping("/api/v1/schedules/groups/{id}")
    GroupView group(@PathVariable UUID id) {
        return schedules.group(id);
    }

    @PostMapping("/api/v1/schedules/groups")
    @ResponseStatus(HttpStatus.CREATED)
    GroupView createGroup(@RequestBody ScheduleService.GroupCommand c) {
        return schedules.createGroup(c);
    }

    @PutMapping("/api/v1/schedules/groups/{id}")
    GroupView updateGroup(@PathVariable UUID id, @RequestBody ScheduleService.GroupCommand c) {
        return schedules.updateGroup(id, c);
    }

    @DeleteMapping("/api/v1/schedules/groups/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void deleteGroup(@PathVariable UUID id) {
        schedules.deleteGroup(id);
    }

    @PutMapping("/api/v1/schedules/groups/{id}/members")
    GroupView members(@PathVariable UUID id, @RequestBody List<ScheduleService.MemberInput> members) {
        return schedules.updateMembers(id, members);
    }

    @PostMapping("/api/v1/schedules/groups/{id}/shift")
    GroupView shift(@PathVariable UUID id, @RequestBody ShiftRequest r) {
        return schedules.shiftArrival(id, new HashSet<>(r.employeeIds()), r.days());
    }

    @PostMapping("/api/v1/schedules/groups/{id}/marks")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void mark(@PathVariable UUID id, @RequestBody MarkRequest r) {
        schedules.mark(id, r.employeeId(), r.from(), r.to(), r.kind(), r.comment());
    }

    @PostMapping("/api/v1/schedules/groups/{id}/approve")
    GroupView approve(@PathVariable UUID id) {
        return schedules.approve(id);
    }

    @GetMapping("/api/v1/schedules/groups/{id}/grid")
    Grid grid(@PathVariable UUID id,
              @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
              @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return schedules.grid(id, from, to);
    }
}
