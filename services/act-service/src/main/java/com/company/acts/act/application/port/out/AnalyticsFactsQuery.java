package com.company.acts.act.application.port.out;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/** Все акты и записи табеля — для полной пересборки данных analytics-service. */
public interface AnalyticsFactsQuery {

    record ActFact(UUID id, String number, String status, UUID organizationId, UUID equipmentId, UUID siteId) {
    }

    record TimeFact(UUID actId, UUID engineerId, LocalDate workDate, int workMinutes) {
    }

    List<ActFact> acts();

    List<TimeFact> timeEntries();
}
