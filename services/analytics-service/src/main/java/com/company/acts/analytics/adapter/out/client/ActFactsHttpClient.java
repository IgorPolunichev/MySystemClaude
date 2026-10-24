package com.company.acts.analytics.adapter.out.client;

import com.company.acts.analytics.application.port.out.ActFactsClient;
import com.company.acts.analytics.application.port.out.FactsStore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.time.Duration;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/** Все факты из act-service для пересборки (эндпоинт доступен администратору). */
@Component
class ActFactsHttpClient implements ActFactsClient {

    @JsonIgnoreProperties(ignoreUnknown = true)
    record ActDto(UUID id, String number, String status, UUID organizationId, UUID equipmentId, UUID siteId) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record TimeDto(UUID actId, UUID engineerId, LocalDate workDate, int workMinutes) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record FactsDto(List<ActDto> acts, List<TimeDto> timeEntries) {
    }

    private final RestClient client;

    ActFactsHttpClient(@Value("${app.clients.act-url:http://localhost:8083}") String baseUrl) {
        this.client = BearerRelay.client(baseUrl, Duration.ofMinutes(2));
    }

    @Override
    public Facts fetchAll() {
        FactsDto dto = client.get().uri("/api/v1/acts/analytics-facts").retrieve().body(FactsDto.class);
        if (dto == null) {
            return new Facts(List.of(), List.of());
        }
        return new Facts(
                dto.acts() == null ? List.of() : dto.acts().stream().map(a -> new FactsStore.ActFact(a.id(), a.number(),
                        a.status(), a.organizationId(), a.equipmentId(), a.siteId())).toList(),
                dto.timeEntries() == null ? List.of() : dto.timeEntries().stream().map(t -> new FactsStore.TimeFact(
                        t.actId(), t.engineerId(), t.workDate(), t.workMinutes())).toList());
    }
}
