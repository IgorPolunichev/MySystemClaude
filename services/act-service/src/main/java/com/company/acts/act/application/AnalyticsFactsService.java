package com.company.acts.act.application;

import com.company.acts.act.application.port.out.AnalyticsFactsQuery;
import java.util.List;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Выгрузка фактов для пересборки аналитики (раздел 9 SPEC.md) — только администратору. */
@Service
public class AnalyticsFactsService {

    public record Facts(List<AnalyticsFactsQuery.ActFact> acts, List<AnalyticsFactsQuery.TimeFact> timeEntries) {
    }

    private final AnalyticsFactsQuery query;

    public AnalyticsFactsService(AnalyticsFactsQuery query) {
        this.query = query;
    }

    @PreAuthorize("hasRole('ADMIN')")
    @Transactional(readOnly = true)
    public Facts facts() {
        return new Facts(query.acts(), query.timeEntries());
    }
}
