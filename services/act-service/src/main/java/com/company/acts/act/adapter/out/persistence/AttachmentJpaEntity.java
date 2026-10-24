package com.company.acts.act.adapter.out.persistence;

import com.company.acts.act.domain.AttachmentKind;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "act_attachment")
public class AttachmentJpaEntity {

    @Id
    @Column(name = "id")
    UUID id;
    @Column(name = "act_id")
    UUID actId;
    @Column(name = "media_id")
    UUID mediaId;
    @Enumerated(EnumType.STRING)
    @Column(name = "kind")
    AttachmentKind kind;
    @Column(name = "file_name")
    String fileName;
    @Column(name = "content_type")
    String contentType;
    @Column(name = "size_bytes")
    long sizeBytes;
    @Column(name = "comment")
    String comment;
    @Column(name = "uploaded_by")
    String uploadedBy;
    @Column(name = "uploaded_at")
    Instant uploadedAt;
}
