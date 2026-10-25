package com.company.acts.analytics.adapter.out.persistence;

import com.company.acts.analytics.application.port.out.ScheduleStore;
import com.company.acts.analytics.domain.schedule.Crew;
import com.company.acts.analytics.domain.schedule.Member;
import com.company.acts.analytics.domain.schedule.ScheduleMode;
import com.company.acts.analytics.domain.schedule.ScheduleTemplate;
import com.company.acts.analytics.domain.schedule.ShiftKind;
import com.company.acts.analytics.domain.schedule.ShiftTime;
import com.company.acts.analytics.domain.schedule.Track;
import com.company.acts.common.core.ConflictException;
import java.sql.Date;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Time;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
class JdbcScheduleStore implements ScheduleStore {

    private final JdbcTemplate jdbc;

    JdbcScheduleStore(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    // ---------- Шаблоны ----------

    @Override
    public List<StoredTemplate> templates() {
        return jdbc.query("select * from schedule_template order by lower(name)", (rs, n) -> template(rs));
    }

    @Override
    public Optional<StoredTemplate> template(UUID id) {
        return jdbc.query("select * from schedule_template where id = ?", (rs, n) -> template(rs), id).stream().findFirst();
    }

    @Override
    public void saveTemplate(UUID id, ScheduleTemplate t, String user) {
        jdbc.update("""
                insert into schedule_template (id, name, mode, work_days, off_days, day_start, day_end, day_break,
                    night_start, night_end, night_break, switch_after_days, rest_after_nights, overlap_days, day_priority,
                    updated_by)
                values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                on conflict (id) do update set name = excluded.name, mode = excluded.mode, work_days = excluded.work_days,
                    off_days = excluded.off_days, day_start = excluded.day_start, day_end = excluded.day_end,
                    day_break = excluded.day_break, night_start = excluded.night_start, night_end = excluded.night_end,
                    night_break = excluded.night_break, switch_after_days = excluded.switch_after_days,
                    rest_after_nights = excluded.rest_after_nights, overlap_days = excluded.overlap_days,
                    day_priority = excluded.day_priority, updated_by = excluded.updated_by, updated_at = now()
                """, id, t.name(), t.mode().name(), t.workDays(), t.offDays(), Time.valueOf(t.day().start()),
                Time.valueOf(t.day().end()), t.day().breakMinutes(),
                t.night() == null ? null : Time.valueOf(t.night().start()),
                t.night() == null ? null : Time.valueOf(t.night().end()),
                t.night() == null ? null : t.night().breakMinutes(), t.switchAfterDays(), t.restAfterNights(),
                t.overlapDays(), t.dayPriority(), user);
    }

    @Override
    public boolean templateInUse(UUID id) {
        Integer n = jdbc.queryForObject("select count(*) from schedule_group where template_id = ?", Integer.class, id);
        return n != null && n > 0;
    }

    @Override
    public void deleteTemplate(UUID id) {
        jdbc.update("delete from schedule_template where id = ?", id);
    }

    private static StoredTemplate template(ResultSet rs) throws SQLException {
        Time ns = rs.getTime("night_start");
        ShiftTime night = ns == null ? null
                : new ShiftTime(ns.toLocalTime(), rs.getTime("night_end").toLocalTime(), rs.getInt("night_break"));
        return new StoredTemplate(rs.getObject("id", UUID.class), new ScheduleTemplate(rs.getString("name"),
                ScheduleMode.valueOf(rs.getString("mode")), rs.getInt("work_days"), rs.getInt("off_days"),
                new ShiftTime(rs.getTime("day_start").toLocalTime(), rs.getTime("day_end").toLocalTime(),
                        rs.getInt("day_break")), night, rs.getInt("switch_after_days"), rs.getBoolean("rest_after_nights"),
                rs.getInt("overlap_days"), rs.getBoolean("day_priority")));
    }

    // ---------- Группы ----------

    @Override
    public List<Group> groups() {
        return jdbc.query("select * from schedule_group order by lower(name)", (rs, n) -> group(rs));
    }

    @Override
    public Optional<Group> group(UUID id) {
        return jdbc.query("select * from schedule_group where id = ?", (rs, n) -> group(rs), id).stream().findFirst();
    }

    @Override
    public long saveGroup(Group g) {
        int updated = jdbc.update("""
                update schedule_group set name = ?, department_id = ?, site_id = ?, site_name = ?, template_id = ?,
                    start_date = ?, required_day = ?, required_night = ?, approved_at = ?, approved_by = ?,
                    version = version + 1, updated_at = now()
                where id = ? and version = ?
                """, g.name(), g.departmentId(), g.siteId(), g.siteName(), g.templateId(), Date.valueOf(g.startDate()),
                g.requiredDay(), g.requiredNight(), g.approvedAt() == null ? null : Timestamp.from(g.approvedAt()),
                g.approvedBy(), g.id(), g.version());
        if (updated == 1) {
            return g.version() + 1;
        }
        if (group(g.id()).isPresent()) {
            throw ConflictException.staleVersion();
        }
        jdbc.update("""
                insert into schedule_group (id, name, department_id, site_id, site_name, template_id, start_date,
                    required_day, required_night, approved_at, approved_by, version)
                values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 0)
                """, g.id(), g.name(), g.departmentId(), g.siteId(), g.siteName(), g.templateId(),
                Date.valueOf(g.startDate()), g.requiredDay(), g.requiredNight(),
                g.approvedAt() == null ? null : Timestamp.from(g.approvedAt()), g.approvedBy());
        return 0;
    }

    @Override
    public void deleteGroup(UUID id) {
        jdbc.update("delete from schedule_group where id = ?", id);
    }

    private static Group group(ResultSet rs) throws SQLException {
        Timestamp approved = rs.getTimestamp("approved_at");
        return new Group(rs.getObject("id", UUID.class), rs.getString("name"), rs.getObject("department_id", UUID.class),
                rs.getObject("site_id", UUID.class), rs.getString("site_name"), rs.getObject("template_id", UUID.class),
                rs.getDate("start_date").toLocalDate(), rs.getInt("required_day"), rs.getInt("required_night"),
                approved == null ? null : approved.toInstant(), rs.getString("approved_by"), rs.getLong("version"));
    }

    // ---------- Состав ----------

    @Override
    public List<Member> members(UUID groupId) {
        return jdbc.query("select * from schedule_member where group_id = ? order by position", (rs, n) -> new Member(
                rs.getObject("employee_id", UUID.class), rs.getString("name"), Crew.valueOf(rs.getString("crew")),
                Track.valueOf(rs.getString("track")), rs.getInt("offset_days"), rs.getObject("partner_id", UUID.class)),
                groupId);
    }

    @Override
    public void replaceMembers(UUID groupId, List<Member> members) {
        jdbc.update("delete from schedule_member where group_id = ?", groupId);
        int position = 1;
        for (Member m : members) {
            jdbc.update("""
                    insert into schedule_member (group_id, employee_id, name, crew, track, offset_days, partner_id, position)
                    values (?, ?, ?, ?, ?, ?, ?, ?)
                    """, groupId, m.employeeId(), m.name() == null ? "—" : m.name(), m.crew().name(), m.track().name(),
                    m.offsetDays(), m.partnerId(), position++);
        }
    }

    // ---------- Отметки ----------

    @Override
    public Map<UUID, Map<LocalDate, ShiftKind>> overrides(UUID groupId, LocalDate from, LocalDate to) {
        Map<UUID, Map<LocalDate, ShiftKind>> result = new HashMap<>();
        for (Mark o : overrideList(groupId, from, to)) {
            result.computeIfAbsent(o.employeeId(), k -> new HashMap<>()).put(o.day(), o.kind());
        }
        return result;
    }

    @Override
    public List<Mark> overrideList(UUID groupId, LocalDate from, LocalDate to) {
        return jdbc.query("select * from schedule_override where group_id = ? and day between ? and ? order by day",
                (rs, n) -> new Mark(rs.getObject("employee_id", UUID.class), rs.getDate("day").toLocalDate(),
                        ShiftKind.valueOf(rs.getString("kind")), rs.getString("comment")),
                groupId, Date.valueOf(from), Date.valueOf(to));
    }

    @Override
    public void setOverride(UUID groupId, UUID employeeId, LocalDate day, ShiftKind kind, String comment, String user) {
        if (kind == null) {
            jdbc.update("delete from schedule_override where group_id = ? and employee_id = ? and day = ?", groupId,
                    employeeId, Date.valueOf(day));
            return;
        }
        jdbc.update("""
                insert into schedule_override (group_id, employee_id, day, kind, comment, updated_by)
                values (?, ?, ?, ?, ?, ?)
                on conflict (group_id, employee_id, day) do update set kind = excluded.kind, comment = excluded.comment,
                    updated_by = excluded.updated_by
                """, groupId, employeeId, Date.valueOf(day), kind.name(), comment, user);
    }

    @Override
    public List<UUID> groupsOf(UUID employeeId) {
        return new ArrayList<>(jdbc.query("select group_id from schedule_member where employee_id = ?",
                (rs, n) -> rs.getObject("group_id", UUID.class), employeeId));
    }
}
