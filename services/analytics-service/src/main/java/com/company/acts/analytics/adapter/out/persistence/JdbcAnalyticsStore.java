package com.company.acts.analytics.adapter.out.persistence;

import com.company.acts.analytics.application.port.out.CalendarStore;
import com.company.acts.analytics.application.port.out.FactsStore;
import com.company.acts.analytics.application.port.out.NormStore;
import java.math.BigDecimal;
import java.sql.Date;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;

/** Read model, нормы и календарь на SQL: агрегаты считаются в PostgreSQL. */
@Component
class JdbcAnalyticsStore implements FactsStore, NormStore, CalendarStore {

    private final JdbcTemplate jdbc;
    private final NamedParameterJdbcTemplate named;

    JdbcAnalyticsStore(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
        this.named = new NamedParameterJdbcTemplate(jdbc);
    }

    // ---------- Факты ----------

    @Override
    public boolean markProcessed(UUID eventId) {
        return jdbc.update("insert into processed_event (event_id) values (?) on conflict do nothing", eventId) == 1;
    }

    @Override
    public void upsertAct(ActFact a) {
        jdbc.update("""
                insert into fact_act (act_id, number, status, organization_id, equipment_id, site_id, updated_at)
                values (?, ?, ?, ?, ?, ?, now())
                on conflict (act_id) do update set number = excluded.number, status = excluded.status,
                    organization_id = excluded.organization_id, equipment_id = excluded.equipment_id,
                    site_id = excluded.site_id, updated_at = now()
                """, a.actId(), a.number(), a.status(), a.organizationId(), a.equipmentId(), a.siteId());
    }

    @Override
    public void deleteAct(UUID actId) {
        jdbc.update("delete from fact_time where act_id = ?", actId);
        jdbc.update("delete from fact_act where act_id = ?", actId);
    }

    @Override
    public void upsertTime(TimeFact t) {
        if (t.workMinutes() <= 0) {
            jdbc.update("delete from fact_time where act_id = ? and engineer_id = ? and work_date = ?",
                    t.actId(), t.engineerId(), Date.valueOf(t.workDate()));
            return;
        }
        jdbc.update("""
                insert into fact_time (act_id, engineer_id, work_date, work_minutes) values (?, ?, ?, ?)
                on conflict (act_id, engineer_id, work_date) do update set work_minutes = excluded.work_minutes
                """, t.actId(), t.engineerId(), Date.valueOf(t.workDate()), t.workMinutes());
    }

    @Override
    public void replaceAll(List<ActFact> acts, List<TimeFact> times) {
        jdbc.update("delete from fact_time");
        jdbc.update("delete from fact_act");
        acts.forEach(this::upsertAct);
        times.forEach(this::upsertTime);
    }

    @Override
    public List<EngineerTotal> engineerTotals(LocalDate from, LocalDate to, UUID organizationId,
                                              Collection<UUID> engineerIds) {
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("from", Date.valueOf(from)).addValue("to", Date.valueOf(to))
                .addValue("ids", engineerIds);
        String org = "";
        if (organizationId != null) {
            org = " and a.organization_id = :org";
            params.addValue("org", organizationId);
        }
        return named.query("""
                select t.engineer_id, sum(t.work_minutes) as minutes, count(distinct t.act_id) as acts,
                       count(distinct a.equipment_id) as machines
                from fact_time t join fact_act a on a.act_id = t.act_id
                where a.status in ('OPEN', 'CLOSED') and t.work_date between :from and :to
                  and t.engineer_id in (:ids)""" + org + " group by t.engineer_id", params,
                (rs, n) -> new EngineerTotal(rs.getObject("engineer_id", UUID.class), rs.getLong("minutes"),
                        rs.getInt("acts"), rs.getInt("machines")));
    }

    @Override
    public List<DayMinutes> dailyMinutes(UUID engineerId, LocalDate from, LocalDate to) {
        return jdbc.query("""
                select t.work_date, sum(t.work_minutes) as minutes
                from fact_time t join fact_act a on a.act_id = t.act_id
                where a.status in ('OPEN', 'CLOSED') and t.engineer_id = ? and t.work_date between ? and ?
                group by t.work_date order by t.work_date
                """, (rs, n) -> new DayMinutes(rs.getDate("work_date").toLocalDate(), rs.getLong("minutes")),
                engineerId, Date.valueOf(from), Date.valueOf(to));
    }

    // ---------- Нормы ----------

    @Override
    public Map<UUID, Map<YearMonth, BigDecimal>> engineerNorms(YearMonth from, YearMonth to) {
        return norms("select engineer_id as id, month, hours from norm_engineer where month between ? and ?", from, to);
    }

    @Override
    public Map<UUID, Map<YearMonth, BigDecimal>> departmentNorms(YearMonth from, YearMonth to) {
        return norms("select department_id as id, month, hours from norm_department where month between ? and ?",
                from, to);
    }

    private Map<UUID, Map<YearMonth, BigDecimal>> norms(String sql, YearMonth from, YearMonth to) {
        Map<UUID, Map<YearMonth, BigDecimal>> result = new HashMap<>();
        jdbc.query(sql, rs -> {
            result.computeIfAbsent(rs.getObject("id", UUID.class), k -> new HashMap<>())
                    .put(YearMonth.from(rs.getDate("month").toLocalDate()), rs.getBigDecimal("hours"));
        }, Date.valueOf(from.atDay(1)), Date.valueOf(to.atDay(1)));
        return result;
    }

    @Override
    public void setEngineerNorm(UUID engineerId, YearMonth month, BigDecimal hours, String user) {
        if (hours == null) {
            jdbc.update("delete from norm_engineer where engineer_id = ? and month = ?", engineerId,
                    Date.valueOf(month.atDay(1)));
            return;
        }
        jdbc.update("""
                insert into norm_engineer (engineer_id, month, hours, updated_by) values (?, ?, ?, ?)
                on conflict (engineer_id, month) do update set hours = excluded.hours, updated_by = excluded.updated_by,
                    updated_at = now()
                """, engineerId, Date.valueOf(month.atDay(1)), hours, user);
    }

    @Override
    public void setDepartmentNorm(UUID departmentId, YearMonth month, BigDecimal hours, String user) {
        if (hours == null) {
            jdbc.update("delete from norm_department where department_id = ? and month = ?", departmentId,
                    Date.valueOf(month.atDay(1)));
            return;
        }
        jdbc.update("""
                insert into norm_department (department_id, month, hours, updated_by) values (?, ?, ?, ?)
                on conflict (department_id, month) do update set hours = excluded.hours,
                    updated_by = excluded.updated_by, updated_at = now()
                """, departmentId, Date.valueOf(month.atDay(1)), hours, user);
    }

    @Override
    public int copyMonth(YearMonth from, YearMonth to, String user) {
        int engineers = jdbc.update("""
                insert into norm_engineer (engineer_id, month, hours, updated_by)
                select engineer_id, ?, hours, ? from norm_engineer where month = ?
                on conflict (engineer_id, month) do nothing
                """, Date.valueOf(to.atDay(1)), user, Date.valueOf(from.atDay(1)));
        int departments = jdbc.update("""
                insert into norm_department (department_id, month, hours, updated_by)
                select department_id, ?, hours, ? from norm_department where month = ?
                on conflict (department_id, month) do nothing
                """, Date.valueOf(to.atDay(1)), user, Date.valueOf(from.atDay(1)));
        return engineers + departments;
    }

    // ---------- Календарь ----------

    @Override
    public Map<LocalDate, Boolean> overrides(LocalDate from, LocalDate to) {
        Map<LocalDate, Boolean> result = new HashMap<>();
        jdbc.query("select day, is_working from calendar_day where day between ? and ?",
                rs -> {
                    result.put(rs.getDate("day").toLocalDate(), rs.getBoolean("is_working"));
                }, Date.valueOf(from), Date.valueOf(to));
        return result;
    }

    @Override
    public void set(LocalDate day, Boolean working, String comment) {
        if (working == null) {
            jdbc.update("delete from calendar_day where day = ?", Date.valueOf(day));
            return;
        }
        jdbc.update("""
                insert into calendar_day (day, is_working, comment) values (?, ?, ?)
                on conflict (day) do update set is_working = excluded.is_working, comment = excluded.comment
                """, Date.valueOf(day), working, comment);
    }
}
