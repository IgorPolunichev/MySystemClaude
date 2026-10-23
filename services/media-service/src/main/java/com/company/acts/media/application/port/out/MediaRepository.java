package com.company.acts.media.application.port.out;

import com.company.acts.media.domain.MediaObject;
import java.util.Optional;
import java.util.UUID;

public interface MediaRepository {

    Optional<MediaObject> findById(UUID id);

    MediaObject save(MediaObject media);

    void markDeleted(UUID id);
}
