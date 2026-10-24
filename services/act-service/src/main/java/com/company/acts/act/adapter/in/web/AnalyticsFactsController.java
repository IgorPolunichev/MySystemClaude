package com.company.acts.act.adapter.in.web;

import com.company.acts.act.application.AnalyticsFactsService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/** Факты для полной пересборки аналитики; вызывает analytics-service от имени администратора. */
@RestController
class AnalyticsFactsController {

    private final AnalyticsFactsService facts;

    AnalyticsFactsController(AnalyticsFactsService facts) {
        this.facts = facts;
    }

    @GetMapping("/api/v1/acts/analytics-facts")
    AnalyticsFactsService.Facts facts() {
        return facts.facts();
    }
}
