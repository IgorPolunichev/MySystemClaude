package com.company.acts.common.events;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Конверт любого события в шине. Полезная нагрузка — версионированный record из этого модуля.
 * Изменения схем только обратно совместимые; несовместимое изменение — новый {@code eventVersion}.
 *
 * @param eventId       уникальный id события, по нему обработчики обеспечивают идемпотентность
 * @param eventType     тип события, см. {@link EventTypes}
 * @param eventVersion  версия схемы полезной нагрузки
 * @param occurredAt    момент события (UTC)
 * @param actorId       кто вызвал изменение (id пользователя Keycloak) или {@code system}
 * @param correlationId сквозной id запроса
 * @param aggregateId   id агрегата, с которым произошло событие (акт, техника и т. п.)
 * @param payload       данные события
 */
public record EventEnvelope<T>(
        UUID eventId,
        String eventType,
        int eventVersion,
        Instant occurredAt,
        String actorId,
        String correlationId,
        UUID aggregateId,
        T payload) {

    public EventEnvelope {
        Objects.requireNonNull(eventId, "eventId");
        Objects.requireNonNull(eventType, "eventType");
        Objects.requireNonNull(occurredAt, "occurredAt");
        if (eventVersion < 1) {
            throw new IllegalArgumentException("eventVersion должен быть >= 1");
        }
    }
}
