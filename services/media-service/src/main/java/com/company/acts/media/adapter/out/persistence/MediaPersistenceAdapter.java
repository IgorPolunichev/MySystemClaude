package com.company.acts.media.adapter.out.persistence;

import com.company.acts.media.application.port.out.MediaRepository;
import com.company.acts.media.domain.MediaObject;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
class MediaPersistenceAdapter implements MediaRepository {

    private final MediaJpaRepository repository;

    MediaPersistenceAdapter(MediaJpaRepository repository) {
        this.repository = repository;
    }

    @Override
    public Optional<MediaObject> findById(UUID id) {
        return repository.findByIdAndDeletedAtIsNull(id).map(MediaPersistenceAdapter::toDomain);
    }

    @Override
    public MediaObject save(MediaObject m) {
        MediaJpaEntity e = new MediaJpaEntity();
        e.id = m.id();
        e.ownerType = m.ownerType();
        e.ownerId = m.ownerId();
        e.originalFilename = m.originalFilename();
        e.contentType = m.contentType();
        e.sizeBytes = m.sizeBytes();
        e.width = m.width();
        e.height = m.height();
        e.sha256 = m.sha256();
        e.objectKey = m.objectKey();
        e.thumbnailKey = m.thumbnailKey();
        e.createdBy = m.createdBy();
        e.createdAt = m.createdAt();
        return toDomain(repository.saveAndFlush(e));
    }

    @Override
    public void markDeleted(UUID id) {
        repository.findByIdAndDeletedAtIsNull(id).ifPresent(e -> {
            e.deletedAt = Instant.now();
            repository.saveAndFlush(e);
        });
    }

    private static MediaObject toDomain(MediaJpaEntity e) {
        return new MediaObject(e.id, e.ownerType, e.ownerId, e.originalFilename, e.contentType, e.sizeBytes, e.width,
                e.height, e.sha256, e.objectKey, e.thumbnailKey, e.createdBy, e.createdAt);
    }
}
