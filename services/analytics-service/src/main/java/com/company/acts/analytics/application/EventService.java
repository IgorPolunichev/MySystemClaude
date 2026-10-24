package com.company.acts.analytics.application;

import com.company.acts.analytics.application.port.out.FactsStore;
import com.company.acts.common.events.EventTypes;
import java.time.LocalDate;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Обновление read model из событий act-service. Каждое событие применяется ровно один раз (по eventId) в той
 * же транзакции, что и изменение данных.
 */
@Service
public class EventService {

    private static final Logger log = LoggerFactory.getLogger(EventService.class);

    private final FactsStore facts;

    public EventService(FactsStore facts) {
        this.facts = facts;
    }

    /** Конверт события (раздел 4 SPEC.md) уже разобран в Map. */
    @Transactional
    @SuppressWarnings("unchecked")
    public void apply(Map<String, Object> envelope) {
        UUID eventId = UUID.fromString(String.valueOf(envelope.get("eventId")));
        if (!facts.markProcessed(eventId)) {
            return;
        }
        String type = String.valueOf(envelope.get("eventType"));
        UUID actId = UUID.fromString(String.valueOf(envelope.get("aggregateId")));
        Map<String, Object> p = envelope.get("payload") instanceof Map<?, ?> m ? (Map<String, Object>) m : Map.of();
        switch (type) {
            case EventTypes.ACT_CREATED, EventTypes.ACT_UPDATED, EventTypes.ACT_CLOSED, EventTypes.ACT_REOPENED,
                 EventTypes.ACT_CANCELLED -> {
                if (p.get("status") != null) {
                    facts.upsertAct(new FactsStore.ActFact(actId, text(p.get("number")), text(p.get("status")),
                            uuid(p.get("organizationId")), uuid(p.get("equipmentId")), uuid(p.get("siteId"))));
                }
            }
            case EventTypes.ACT_DELETED -> facts.deleteAct(actId);
            case EventTypes.TIME_ENTRY_CHANGED -> facts.upsertTime(new FactsStore.TimeFact(actId,
                    UUID.fromString(String.valueOf(p.get("engineerId"))), LocalDate.parse(String.valueOf(p.get("workDate"))),
                    p.get("workMinutes") == null ? 0 : (int) Double.parseDouble(String.valueOf(p.get("workMinutes")))));
            default -> log.debug("Событие {} не влияет на аналитику", type);
        }
    }

    private static String text(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private static UUID uuid(Object value) {
        return value == null ? null : UUID.fromString(String.valueOf(value));
    }
}
