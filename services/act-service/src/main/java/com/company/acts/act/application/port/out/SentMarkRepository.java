package com.company.acts.act.application.port.out;

import java.time.Instant;
import java.util.Collection;
import java.util.UUID;

/** Отметка об отправке актов письмом; события обрабатываются один раз (processed_event). */
public interface SentMarkRepository {

    boolean markProcessed(UUID eventId);

    void markSent(Collection<UUID> actIds, Instant at);
}
