package com.company.acts.act.adapter.out.persistence;

import com.company.acts.act.application.port.out.AnalyticsFactsQuery;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
class AnalyticsFactsAdapter implements AnalyticsFactsQuery {

    private final JdbcTemplate jdbc;

    AnalyticsFactsAdapter(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public List<ActFact> acts() {
        return jdbc.query("select id, number, status, org_id, equipment_id, site_id from work_act",
                (rs, n) -> new ActFact(rs.getObject("id", UUID.class), rs.getString("number"), rs.getString("status"),
                        rs.getObject("org_id", UUID.class), rs.getObject("equipment_id", UUID.class),
                        rs.getObject("site_id", UUID.class)));
    }

    @Override
    public List<TimeFact> timeEntries() {
        return jdbc.query("select act_id, engineer_id, work_date, work_minutes from time_entry",
                (rs, n) -> new TimeFact(rs.getObject("act_id", UUID.class), rs.getObject("engineer_id", UUID.class),
                        rs.getDate("work_date").toLocalDate(), rs.getInt("work_minutes")));
    }
}
