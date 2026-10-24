package com.company.acts.act.adapter.out.persistence;

import com.company.acts.act.application.port.out.SentMarkRepository;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Collection;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
class SentMarkAdapter implements SentMarkRepository {

    private final JdbcTemplate jdbc;

    SentMarkAdapter(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public boolean markProcessed(UUID eventId) {
        return jdbc.update("insert into processed_event (event_id) values (?) on conflict do nothing", eventId) == 1;
    }

    @Override
    public void markSent(Collection<UUID> actIds, Instant at) {
        for (UUID id : actIds) {
            jdbc.update("update work_act set last_sent_at = greatest(coalesce(last_sent_at, ?), ?),"
                    + " sent_count = sent_count + 1 where id = ?", Timestamp.from(at), Timestamp.from(at), id);
        }
    }
}
