package com.company.acts.act.application;

import com.company.acts.act.application.port.out.SentMarkRepository;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Событие EmailSent от mail-service: акты письма получают отметку «Отправлен» с датой. */
@Service
public class EmailSentHandler {

    private final SentMarkRepository marks;

    public EmailSentHandler(SentMarkRepository marks) {
        this.marks = marks;
    }

    @Transactional
    @SuppressWarnings("unchecked")
    public void handle(Map<String, Object> envelope) {
        if (!"EmailSent".equals(envelope.get("eventType"))) {
            return;
        }
        UUID eventId = UUID.fromString(String.valueOf(envelope.get("eventId")));
        if (!marks.markProcessed(eventId)) {
            return;
        }
        Map<String, Object> payload = envelope.get("payload") instanceof Map<?, ?> m ? (Map<String, Object>) m : Map.of();
        List<UUID> actIds = payload.get("actIds") instanceof List<?> list
                ? list.stream().map(x -> UUID.fromString(String.valueOf(x))).toList() : List.of();
        Instant at = payload.get("sentAt") == null ? Instant.now() : Instant.parse(String.valueOf(payload.get("sentAt")));
        marks.markSent(actIds, at);
    }
}
