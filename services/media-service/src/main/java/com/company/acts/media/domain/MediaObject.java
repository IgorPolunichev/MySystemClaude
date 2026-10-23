package com.company.acts.media.domain;

import java.time.Instant;
import java.util.UUID;

/**
 * Файл в хранилище: оригинал и превью. Владелец (ownerType + ownerId) — к чему относится файл, например
 * фото акта; сам media-service права владельца не проверяет, это делает сервис-владелец.
 */
public record MediaObject(UUID id, String ownerType, UUID ownerId, String originalFilename, String contentType,
                          long sizeBytes, int width, int height, String sha256, String objectKey,
                          String thumbnailKey, String createdBy, Instant createdAt) {
}
