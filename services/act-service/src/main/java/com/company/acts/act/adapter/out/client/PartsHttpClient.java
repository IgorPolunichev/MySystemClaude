package com.company.acts.act.adapter.out.client;

import com.company.acts.act.application.port.out.PartsClient;
import com.company.acts.common.core.BusinessRuleException;
import com.company.acts.common.core.ConflictException;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;

/** parts-service по HTTP. */
@Component
class PartsHttpClient implements PartsClient {

    @JsonIgnoreProperties(ignoreUnknown = true)
    record PartDto(UUID id) {
    }

    private final RestClient client;

    PartsHttpClient(@Value("${app.clients.parts-url:http://localhost:8082}") String baseUrl) {
        this.client = BearerRelay.client(baseUrl);
    }

    @Override
    public UUID createCatalogItem(String name, String catalogNumber, String unitCode, boolean fluid) {
        Map<String, Object> body = new HashMap<>();
        body.put("name", name);
        body.put("catalogNumber", catalogNumber);
        body.put("defaultUnit", unitCode);
        body.put("fluid", fluid);
        try {
            PartDto created = client.post().uri("/api/v1/parts")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .body(PartDto.class);
            if (created == null) {
                throw new IllegalStateException("Пустой ответ каталога запчастей");
            }
            return created.id();
        } catch (HttpClientErrorException.Conflict e) {
            throw new ConflictException("В каталоге уже есть позиция с таким номером — выберите её из каталога");
        } catch (HttpClientErrorException e) {
            throw new BusinessRuleException("Каталог запчастей не принял позицию: " + e.getStatusText());
        }
    }
}
