package com.company.acts.media.application;

import com.company.acts.common.core.BusinessRuleException;
import com.company.acts.common.core.NotFoundException;
import com.company.acts.common.core.Strings;
import com.company.acts.common.core.Uuid7;
import com.company.acts.media.application.port.out.ImageProcessor;
import com.company.acts.media.application.port.out.MediaRepository;
import com.company.acts.media.application.port.out.ObjectStorage;
import com.company.acts.media.domain.FileType;
import com.company.acts.media.domain.ImageType;
import com.company.acts.media.domain.MediaObject;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Загрузка и выдача фото (раздел 6.6 SPEC.md). Браузер заранее сжимает снимок до 2560 px и переводит в JPEG
 * (с правильным поворотом и без геоданных); сервер проверяет формат по содержимому, делает превью 400 px и
 * кладёт оба файла в хранилище.
 */
@Service
public class MediaService {

    public static final long MAX_SIZE = 25L * 1024 * 1024;
    public static final int THUMBNAIL_SIDE = 400;
    /** Фото техотчёта: только JPEG и PNG. */
    public static final String ACT_PHOTO = "ACT_PHOTO";
    /** Документы акта: сканы, письма, протоколы. */
    public static final String ACT_ATTACHMENT = "ACT_ATTACHMENT";
    private static final Set<String> OWNER_TYPES = Set.of(ACT_PHOTO, ACT_ATTACHMENT);

    /** Содержимое файла для отдачи клиенту. */
    public record Content(byte[] bytes, String contentType, String etag) {
    }

    private final MediaRepository media;
    private final ObjectStorage storage;
    private final ImageProcessor images;

    public MediaService(MediaRepository media, ObjectStorage storage, ImageProcessor images) {
        this.media = media;
        this.storage = storage;
        this.images = images;
    }

    @Transactional
    public MediaObject upload(String filename, byte[] content, String ownerType, UUID ownerId, String user) {
        if (content == null || content.length == 0) {
            throw new BusinessRuleException("file", "Файл пустой");
        }
        if (content.length > MAX_SIZE) {
            throw new BusinessRuleException("file", "Файл больше 25 МБ");
        }
        if (!OWNER_TYPES.contains(ownerType)) {
            throw new BusinessRuleException("ownerType", "Неизвестное назначение файла: " + ownerType);
        }
        FileType type = ACT_PHOTO.equals(ownerType) ? photoType(content) : FileType.detect(content, filename);
        int width = 0;
        int height = 0;
        UUID id = Uuid7.next();
        String prefix = ownerType.toLowerCase() + "/" + ownerId + "/" + id;
        String objectKey = prefix + "." + type.extension();
        String thumbnailKey = null;
        if (type.image()) {
            ImageProcessor.ImageInfo info = images.inspect(content);
            width = info.width();
            height = info.height();
            thumbnailKey = prefix + "-thumb.jpg";
            storage.put(thumbnailKey, images.thumbnail(content, THUMBNAIL_SIDE), FileType.JPEG.contentType());
        }
        storage.put(objectKey, content, type.contentType());

        MediaObject object = new MediaObject(id, ownerType, ownerId, Strings.trimToNull(filename), type.contentType(),
                content.length, width, height, sha256(content), objectKey, thumbnailKey, user, Instant.now());
        return media.save(object);
    }

    @Transactional(readOnly = true)
    public MediaObject get(UUID id) {
        return media.findById(id).orElseThrow(() -> NotFoundException.of("Файл", id));
    }

    /** Оригинал или превью ({@code thumbnail}; есть только у изображений). */
    @Transactional(readOnly = true)
    public Content content(UUID id, boolean thumbnail) {
        MediaObject object = get(id);
        if (thumbnail && object.thumbnailKey() == null) {
            throw new NotFoundException("У файла нет превью");
        }
        String key = thumbnail ? object.thumbnailKey() : object.objectKey();
        return new Content(storage.get(key), thumbnail ? FileType.JPEG.contentType() : object.contentType(),
                "\"" + object.sha256().substring(0, 16) + (thumbnail ? "-t" : "") + "\"");
    }

    private static FileType photoType(byte[] content) {
        return ImageType.detect(content) == ImageType.PNG ? FileType.PNG : FileType.JPEG;
    }

    private static String sha256(byte[] content) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(content));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
