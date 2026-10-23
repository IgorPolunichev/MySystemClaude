package com.company.acts.media.application.port.out;

/** Хранилище файлов (S3-совместимое). */
public interface ObjectStorage {

    void put(String key, byte[] content, String contentType);

    byte[] get(String key);

    void delete(String key);
}
