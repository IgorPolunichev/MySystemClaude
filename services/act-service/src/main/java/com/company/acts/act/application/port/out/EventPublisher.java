package com.company.acts.act.application.port.out;

import java.util.Map;
import java.util.UUID;

/**
 * Публикация событий (раздел 4 SPEC.md) через transactional outbox: событие записывается в той же
 * транзакции, что и изменение данных. Значения payload — строки, числа, логические значения, списки и
 * вложенные Map.
 */
public interface EventPublisher {

    void publish(String eventType, UUID aggregateId, Map<String, Object> payload);
}
