package com.company.acts.analytics.application.port.out;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

/** Read model фактов: акты (статус, заказчик, техника) и продуктивные минуты инженеров по дням. */
public interface FactsStore {

    record ActFact(UUID actId, String number, String status, UUID organizationId, UUID equipmentId, UUID siteId) {
    }

    record TimeFact(UUID actId, UUID engineerId, LocalDate workDate, int workMinutes) {
    }

    /** Итоги инженера за период: минуты, число актов, число машин. Учитываются акты OPEN и CLOSED. */
    record EngineerTotal(UUID engineerId, long minutes, int acts, int machines) {
    }

    record DayMinutes(LocalDate day, long minutes) {
    }

    /** true — событие обработано впервые; false — это повтор, его нужно пропустить. */
    boolean markProcessed(UUID eventId);

    void upsertAct(ActFact act);

    void deleteAct(UUID actId);

    /** minutes = 0 — запись удалена. */
    void upsertTime(TimeFact fact);

    void replaceAll(List<ActFact> acts, List<TimeFact> times);

    List<EngineerTotal> engineerTotals(LocalDate from, LocalDate to, UUID organizationId, Collection<UUID> engineerIds);

    List<DayMinutes> dailyMinutes(UUID engineerId, LocalDate from, LocalDate to);
}
