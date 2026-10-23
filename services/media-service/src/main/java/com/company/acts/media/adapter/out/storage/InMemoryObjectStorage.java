package com.company.acts.media.adapter.out.storage;

import com.company.acts.common.core.NotFoundException;
import com.company.acts.media.application.port.out.ObjectStorage;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/** Хранилище в памяти — для тестов и запуска без S3 ({@code app.storage.type=memory}). */
@Component
@ConditionalOnProperty(name = "app.storage.type", havingValue = "memory")
class InMemoryObjectStorage implements ObjectStorage {

    private final Map<String, byte[]> objects = new ConcurrentHashMap<>();

    @Override
    public void put(String key, byte[] content, String contentType) {
        objects.put(key, content.clone());
    }

    @Override
    public byte[] get(String key) {
        byte[] content = objects.get(key);
        if (content == null) {
            throw new NotFoundException("Файл не найден в хранилище: " + key);
        }
        return content.clone();
    }

    @Override
    public void delete(String key) {
        objects.remove(key);
    }
}
