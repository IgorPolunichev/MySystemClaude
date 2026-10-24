package com.company.acts.media.adapter.in.web;

import com.company.acts.media.application.MediaService;
import com.company.acts.media.domain.MediaObject;
import java.io.IOException;
import java.time.Duration;
import java.util.UUID;
import java.nio.charset.StandardCharsets;
import org.springframework.http.CacheControl;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/v1/media")
class MediaController {

    record MediaResponse(UUID id, String ownerType, UUID ownerId, String originalFilename, String contentType,
                         long sizeBytes, int width, int height) {
        static MediaResponse of(MediaObject m) {
            return new MediaResponse(m.id(), m.ownerType(), m.ownerId(), m.originalFilename(), m.contentType(),
                    m.sizeBytes(), m.width(), m.height());
        }
    }

    private final MediaService media;

    MediaController(MediaService media) {
        this.media = media;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    MediaResponse upload(@RequestParam("file") MultipartFile file, @RequestParam String ownerType,
                         @RequestParam UUID ownerId, Authentication auth) throws IOException {
        return MediaResponse.of(media.upload(file.getOriginalFilename(), file.getBytes(), ownerType, ownerId,
                auth.getName()));
    }

    @GetMapping("/{id}")
    MediaResponse get(@PathVariable UUID id) {
        return MediaResponse.of(media.get(id));
    }

    /** Оригинал или превью; кэшируется браузером — содержимое по id никогда не меняется. */
    @GetMapping("/{id}/content")
    ResponseEntity<byte[]> content(@PathVariable UUID id,
                                   @RequestParam(defaultValue = "original") String variant,
                                   @RequestParam(defaultValue = "false") boolean download) {
        MediaService.Content content = media.content(id, "thumbnail".equals(variant));
        String filename = media.get(id).originalFilename();
        ContentDisposition.Builder disposition = download ? ContentDisposition.attachment() : ContentDisposition.inline();
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(content.contentType()))
                .header(HttpHeaders.CONTENT_DISPOSITION, disposition
                        .filename(filename != null ? filename : id.toString(), StandardCharsets.UTF_8).build().toString())
                .cacheControl(CacheControl.maxAge(Duration.ofDays(30)).cachePrivate().immutable())
                .eTag(content.etag())
                .body(content.bytes());
    }
}
