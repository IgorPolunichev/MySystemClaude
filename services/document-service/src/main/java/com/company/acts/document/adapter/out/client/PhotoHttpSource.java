package com.company.acts.document.adapter.out.client;

import com.company.acts.document.application.port.out.PhotoSource;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/** Оригиналы фото из media-service. */
@Component
class PhotoHttpSource implements PhotoSource {

    private final RestClient client;

    PhotoHttpSource(@Value("${app.clients.media-url:http://localhost:8084}") String baseUrl) {
        this.client = BearerRelay.client(baseUrl);
    }

    @Override
    public byte[] original(UUID mediaId) {
        byte[] content = client.get().uri("/api/v1/media/{id}/content?variant=original", mediaId)
                .retrieve().body(byte[].class);
        if (content == null) {
            throw new IllegalStateException("Пустой ответ media-service для фото " + mediaId);
        }
        return content;
    }
}
