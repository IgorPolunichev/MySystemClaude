package com.company.acts.mail.adapter.out.client;

import com.company.acts.common.core.BusinessRuleException;
import com.company.acts.mail.application.port.out.ActSource;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;

/** Карточка акта из act-service с правами пользователя. */
@Component
class ActHttpSource implements ActSource {

    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd.MM.yyyy");

    @JsonIgnoreProperties(ignoreUnknown = true)
    record OrgDto(UUID id, String name, String shortName) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record EqDto(String brand, String model, String garageNumber, String plateNumber, String serialNumber) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record ActDto(UUID id, String number, String status, OrgDto organization, EqDto equipment, LocalDate workStartDate,
                  LocalDate workEndDate, String shortDescription, BigDecimal totalHours, Instant lastSentAt) {
    }

    private final RestClient client;

    ActHttpSource(@Value("${app.clients.act-url:http://localhost:8083}") String baseUrl) {
        this.client = BearerRelay.client(baseUrl, Duration.ofSeconds(15));
    }

    @Override
    public ActInfo act(UUID id) {
        ActDto a;
        try {
            a = client.get().uri("/api/v1/acts/{id}", id).retrieve().body(ActDto.class);
        } catch (HttpClientErrorException.NotFound | HttpClientErrorException.Forbidden e) {
            throw new BusinessRuleException("actIds", "Акт не найден или нет доступа: " + id);
        }
        if (a == null) {
            throw new BusinessRuleException("actIds", "Акт не найден: " + id);
        }
        String org = a.organization() == null ? null
                : a.organization().shortName() != null ? a.organization().shortName() : a.organization().name();
        return new ActInfo(a.id(), a.number(), a.status(), a.organization() == null ? null : a.organization().id(), org,
                period(a.workStartDate(), a.workEndDate()), equipment(a.equipment()), a.shortDescription(),
                a.totalHours(), a.lastSentAt());
    }

    private static String period(LocalDate start, LocalDate end) {
        if (start == null && end == null) {
            return null;
        }
        if (start == null || end == null || start.equals(end)) {
            return DATE.format(start != null ? start : end);
        }
        return DATE.format(start) + " — " + DATE.format(end);
    }

    private static String equipment(EqDto e) {
        if (e == null) {
            return null;
        }
        List<String> parts = new ArrayList<>();
        parts.add(e.brand() + " " + e.model());
        if (e.garageNumber() != null) {
            parts.add("гар. № " + e.garageNumber());
        } else if (e.plateNumber() != null) {
            parts.add(e.plateNumber());
        } else if (e.serialNumber() != null) {
            parts.add("S/N " + e.serialNumber());
        }
        return String.join(", ", parts);
    }
}
