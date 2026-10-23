package com.company.acts.act;

import static org.assertj.core.api.Assertions.assertThat;

import com.company.acts.common.test.PostgresIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

/** Контекст поднимается на реальном PostgreSQL, миграции Flyway применяются. Требуется Docker. */
@SpringBootTest
class ActApplicationIT extends PostgresIntegrationTest {

    @Autowired
    JdbcTemplate jdbc;

    @Test
    void migrationsApplied() {
        Integer applied = jdbc.queryForObject(
                "select count(*) from flyway_schema_history where success", Integer.class);
        assertThat(applied).isGreaterThanOrEqualTo(1);
        assertThat(jdbc.queryForObject("select count(*) from outbox", Integer.class)).isZero();
    }
}
