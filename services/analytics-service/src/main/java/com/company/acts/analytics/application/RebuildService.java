package com.company.acts.analytics.application;

import com.company.acts.analytics.application.port.out.ActFactsClient;
import com.company.acts.analytics.application.port.out.FactsStore;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Полная пересборка read model из act-service — на случай расхождений (раздел 9 SPEC.md). */
@Service
public class RebuildService {

    public record Result(int acts, int timeEntries) {
    }

    private final ActFactsClient source;
    private final FactsStore facts;

    public RebuildService(ActFactsClient source, FactsStore facts) {
        this.source = source;
        this.facts = facts;
    }

    @PreAuthorize("hasRole('ADMIN')")
    @Transactional
    public Result rebuild() {
        ActFactsClient.Facts all = source.fetchAll();
        facts.replaceAll(all.acts(), all.timeEntries());
        return new Result(all.acts().size(), all.timeEntries().size());
    }
}
