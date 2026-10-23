package com.company.acts.media.adapter.out.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "media_object")
public class MediaJpaEntity {

    @Id
    @Column(name = "id")
    UUID id;
    @Column(name = "owner_type")
    String ownerType;
    @Column(name = "owner_id")
    UUID ownerId;
    @Column(name = "original_filename")
    String originalFilename;
    @Column(name = "content_type")
    String contentType;
    @Column(name = "size_bytes")
    long sizeBytes;
    @Column(name = "width")
    int width;
    @Column(name = "height")
    int height;
    @Column(name = "sha256")
    String sha256;
    @Column(name = "object_key")
    String objectKey;
    @Column(name = "thumbnail_key")
    String thumbnailKey;
    @Column(name = "created_by")
    String createdBy;
    @Column(name = "created_at")
    Instant createdAt;
    @Column(name = "deleted_at")
    Instant deletedAt;
}
