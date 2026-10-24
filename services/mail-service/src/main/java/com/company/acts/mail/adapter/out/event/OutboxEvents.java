package com.company.acts.mail.adapter.out.event;

import com.company.acts.common.core.Uuid7;
import com.company.acts.mail.application.port.out.CurrentUserProvider;
import com.company.acts.mail.application.port.out.EventPublisher;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

/** Transactional outbox mail-service и его отправка в RabbitMQ (как в act-service, ADR 0014). */
final class OutboxEvents {

    static final String EXCHANGE = "acts.events";

    private OutboxEvents() {
    }

    @Component
    static class Publisher implements EventPublisher {

        private final JdbcTemplate jdbc;
        private final CurrentUserProvider users;

        Publisher(JdbcTemplate jdbc, CurrentUserProvider users) {
            this.jdbc = jdbc;
            this.users = users;
        }

        @Override
        public void publish(String eventType, UUID aggregateId, Map<String, Object> payload) {
            UUID eventId = Uuid7.next();
            Instant now = Instant.now();
            Map<String, Object> envelope = new LinkedHashMap<>();
            envelope.put("eventId", eventId.toString());
            envelope.put("eventType", eventType);
            envelope.put("eventVersion", 1);
            envelope.put("occurredAt", now.toString());
            envelope.put("actorId", users.current().username());
            envelope.put("aggregateId", aggregateId.toString());
            envelope.put("payload", payload);
            jdbc.update("insert into outbox (id, aggregate_id, event_type, event_version, payload, created_at)"
                    + " values (?, ?, ?, 1, ?::jsonb, ?)", eventId, aggregateId, eventType, Json.write(envelope),
                    java.sql.Timestamp.from(now));
        }
    }

    /** Отправка outbox в RabbitMQ; включается {@code app.outbox.publish-enabled=true}. */
    @Component
    @ConditionalOnProperty(name = "app.outbox.publish-enabled", havingValue = "true")
    static class Relay {

        private static final int BATCH = 100;
        private static final Logger log = LoggerFactory.getLogger(Relay.class);

        private final JdbcTemplate jdbc;
        private final RabbitTemplate rabbit;
        private final TransactionTemplate tx;

        Relay(JdbcTemplate jdbc, RabbitTemplate rabbit, TransactionTemplate tx) {
            this.jdbc = jdbc;
            this.rabbit = rabbit;
            this.tx = tx;
        }

        @Scheduled(fixedDelayString = "${app.outbox.publish-interval-ms:1000}")
        void publish() {
            try {
                Integer sent;
                do {
                    sent = tx.execute(status -> batch());
                } while (sent != null && sent == BATCH);
            } catch (RuntimeException e) {
                log.warn("Не удалось отправить события из outbox, повторю позже: {}", e.getMessage());
            }
        }

        private int batch() {
            List<Map<String, Object>> rows = jdbc.queryForList("""
                    select id, event_type, payload::text as payload from outbox
                    where published_at is null order by created_at, id limit ? for update skip locked
                    """, BATCH);
            for (Map<String, Object> row : rows) {
                MessageProperties props = new MessageProperties();
                props.setContentType(MessageProperties.CONTENT_TYPE_JSON);
                props.setContentEncoding(StandardCharsets.UTF_8.name());
                props.setMessageId(String.valueOf(row.get("id")));
                props.setType(String.valueOf(row.get("event_type")));
                rabbit.send(EXCHANGE, String.valueOf(row.get("event_type")),
                        new Message(String.valueOf(row.get("payload")).getBytes(StandardCharsets.UTF_8), props));
                jdbc.update("update outbox set published_at = now() where id = ?", row.get("id"));
            }
            return rows.size();
        }
    }

    @Configuration(proxyBeanMethods = false)
    @ConditionalOnProperty(name = "app.outbox.publish-enabled", havingValue = "true")
    static class Exchange {
        @Bean
        TopicExchange mailActsEventsExchange() {
            return new TopicExchange(EXCHANGE, true, false);
        }
    }
}
