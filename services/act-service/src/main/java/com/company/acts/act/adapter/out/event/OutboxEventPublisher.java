package com.company.acts.act.adapter.out.event;

import com.company.acts.act.adapter.out.persistence.OutboxJpaEntity;
import com.company.acts.act.adapter.out.persistence.OutboxJpaRepository;
import com.company.acts.act.application.port.out.CurrentUserProvider;
import com.company.acts.act.application.port.out.EventPublisher;
import com.company.acts.common.core.Uuid7;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;

/**
 * Transactional outbox: событие записывается в таблицу outbox в той же транзакции, что и изменение акта.
 * Отправку в RabbitMQ выполнит публикатор (этап 7, когда появится первый потребитель — analytics-service).
 */
@Component
class OutboxEventPublisher implements EventPublisher {

    private final OutboxJpaRepository repository;
    private final CurrentUserProvider users;

    OutboxEventPublisher(OutboxJpaRepository repository, CurrentUserProvider users) {
        this.repository = repository;
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
        envelope.put("actorId", users.current().userId());
        envelope.put("correlationId", MDC.get("correlationId"));
        envelope.put("aggregateId", aggregateId.toString());
        envelope.put("payload", payload);

        OutboxJpaEntity e = new OutboxJpaEntity();
        e.setId(eventId);
        e.setAggregateId(aggregateId);
        e.setEventType(eventType);
        e.setEventVersion(1);
        e.setPayload(Json.write(envelope));
        e.setCreatedAt(now);
        repository.saveAndFlush(e);
    }
}
