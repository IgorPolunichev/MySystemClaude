package com.company.acts.act.adapter.out.client;

import com.company.acts.act.application.port.out.MediaClient;
import com.company.acts.common.core.BusinessRuleException;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;

/** media-service по HTTP: сведения о загруженном файле. */
@Component
class MediaHttpClient implements MediaClient {

    @JsonIgnoreProperties(ignoreUnknown = true)
    record MediaDto(UUID id, String ownerType, UUID ownerId, String originalFilename, String contentType,
                    long sizeBytes) {
    }

    private final RestClient client;

    MediaHttpClient(@Value("${app.clients.media-url:http://localhost:8084}") String baseUrl) {
        this.client = BearerRelay.client(baseUrl);
    }

    @Override
    public MediaInfo media(UUID id) {
        try {
            MediaDto m = client.get().uri("/api/v1/media/{id}", id).retrieve().body(MediaDto.class);
            if (m == null) {
                throw new BusinessRuleException("mediaId", "Файл не найден");
            }
            return new MediaInfo(m.id(), m.ownerType(), m.ownerId(), m.originalFilename(), m.contentType(),
                    m.sizeBytes());
        } catch (HttpClientErrorException.NotFound e) {
            throw new BusinessRuleException("mediaId", "Файл не найден");
        }
    }
}
