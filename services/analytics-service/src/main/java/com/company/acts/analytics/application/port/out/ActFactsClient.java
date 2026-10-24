package com.company.acts.analytics.application.port.out;

import java.util.List;

/** Все факты из act-service для полной пересборки. */
public interface ActFactsClient {

    record Facts(List<FactsStore.ActFact> acts, List<FactsStore.TimeFact> timeEntries) {
    }

    Facts fetchAll();
}
