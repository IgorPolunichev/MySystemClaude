package com.company.acts.act.application.port.out;

import java.util.UUID;

/** Сведения о загруженном файле из media-service. */
public interface MediaClient {

    record MediaInfo(UUID id, String ownerType, UUID ownerId, String originalFilename, String contentType,
                     long sizeBytes) {
    }

    MediaInfo media(UUID id);
}
