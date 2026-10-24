package com.company.acts.act.adapter.out.event;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
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

/**
 * Отправка событий из outbox в RabbitMQ (обменник {@code acts.events}, ключ маршрутизации — тип события).
 * Раз в секунду берётся пачка неотправленных событий в порядке создания; строки блокируются
 * ({@code FOR UPDATE SKIP LOCKED}), поэтому несколько экземпляров сервиса не отправят одно событие дважды
 * одновременно. Доставка «хотя бы один раз»: потребители отбрасывают повторы по eventId.
 * Включается {@code app.outbox.publish-enabled=true} (в Docker Compose включено, в тестах выключено).
 */
@Component
@ConditionalOnProperty(name = "app.outbox.publish-enabled", havingValue = "true")
class OutboxRelay {

    static final String EXCHANGE = "acts.events";
    private static final int BATCH = 100;
    private static final Logger log = LoggerFactory.getLogger(OutboxRelay.class);

    private final JdbcTemplate jdbc;
    private final RabbitTemplate rabbit;
    private final TransactionTemplate tx;

    OutboxRelay(JdbcTemplate jdbc, RabbitTemplate rabbit, TransactionTemplate tx) {
        this.jdbc = jdbc;
        this.rabbit = rabbit;
        this.tx = tx;
    }

    @Scheduled(fixedDelayString = "${app.outbox.publish-interval-ms:1000}")
    void publish() {
        try {
            Integer sent;
            do {
                sent = tx.execute(status -> publishBatch());
            } while (sent != null && sent == BATCH);
        } catch (RuntimeException e) {
            log.warn("Не удалось отправить события из outbox, повторю позже: {}", e.getMessage());
        }
    }

    private int publishBatch() {
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
            byte[] body = String.valueOf(row.get("payload")).getBytes(StandardCharsets.UTF_8);
            rabbit.send(EXCHANGE, String.valueOf(row.get("event_type")), new Message(body, props));
            jdbc.update("update outbox set published_at = now() where id = ?", row.get("id"));
        }
        return rows.size();
    }

    /** Обменник событий; объявляется в RabbitMQ при первом подключении. */
    @Configuration(proxyBeanMethods = false)
    @ConditionalOnProperty(name = "app.outbox.publish-enabled", havingValue = "true")
    static class Exchange {
        @Bean
        TopicExchange actsEventsExchange() {
            return new TopicExchange(EXCHANGE, true, false);
        }
    }
}
