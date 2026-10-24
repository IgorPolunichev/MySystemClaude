package com.company.acts.document.application.port.out;

import java.util.UUID;

/** Оригиналы фото из media-service. */
public interface PhotoSource {

    byte[] original(UUID mediaId);
}
