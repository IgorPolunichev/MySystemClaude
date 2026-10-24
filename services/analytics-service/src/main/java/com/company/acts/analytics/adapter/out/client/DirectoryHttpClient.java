package com.company.acts.analytics.adapter.out.client;

import com.company.acts.analytics.application.port.out.DirectoryClient;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;

/** Инженеры и отделы из directory-service (с правами пользователя, список инженеров видят все). */
@Component
class DirectoryHttpClient implements DirectoryClient {

    @JsonIgnoreProperties(ignoreUnknown = true)
    record EngineerDto(UUID id, String keycloakUserId, String fullName, UUID serviceDepartmentId,
                       String serviceDepartmentName, boolean active) {
        EngineerRef ref() {
            return new EngineerRef(id, keycloakUserId, fullName, serviceDepartmentId, serviceDepartmentName, active);
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record EngineerPage(List<EngineerDto> items, long totalElements) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record DepartmentDto(UUID id, String name) {
    }

    private final RestClient client;

    DirectoryHttpClient(@Value("${app.clients.directory-url:http://localhost:8081}") String baseUrl) {
        this.client = BearerRelay.client(baseUrl, Duration.ofSeconds(10));
    }

    @Override
    public List<EngineerRef> engineers() {
        List<EngineerRef> result = new ArrayList<>();
        for (int page = 0; page < 50; page++) {
            EngineerPage chunk = client.get().uri("/api/v1/engineers?page={p}&size=200", page).retrieve()
                    .body(EngineerPage.class);
            if (chunk == null || chunk.items() == null || chunk.items().isEmpty()) {
                break;
            }
            chunk.items().forEach(e -> result.add(e.ref()));
            if (result.size() >= chunk.totalElements()) {
                break;
            }
        }
        return result;
    }

    @Override
    public List<DepartmentRef> departments() {
        DepartmentDto[] list = client.get().uri("/api/v1/service-departments").retrieve().body(DepartmentDto[].class);
        List<DepartmentRef> result = new ArrayList<>();
        if (list != null) {
            for (DepartmentDto d : list) {
                result.add(new DepartmentRef(d.id(), d.name()));
            }
        }
        return result;
    }

    @Override
    public EngineerRef me() {
        try {
            EngineerDto e = client.get().uri("/api/v1/engineers/me").retrieve().body(EngineerDto.class);
            return e == null ? null : e.ref();
        } catch (HttpClientErrorException.NotFound e) {
            return null;
        }
    }
}
