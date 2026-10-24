package com.company.acts.act.domain;

import com.company.acts.common.core.BusinessRuleException;
import com.company.acts.common.core.Strings;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/** Документ, прикреплённый к акту (скан подписанного акта или другой файл); сам файл — в media-service. */
public record ActAttachment(UUID id, UUID actId, UUID mediaId, AttachmentKind kind, String fileName,
                            String contentType, long sizeBytes, String comment, String uploadedBy,
                            Instant uploadedAt) {

    public ActAttachment {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(actId, "actId");
        if (mediaId == null) {
            throw new BusinessRuleException("mediaId", "Не указан файл");
        }
        kind = kind == null ? AttachmentKind.OTHER : kind;
        comment = Strings.trimToNull(comment);
        if (comment != null && comment.length() > 300) {
            throw new BusinessRuleException("comment", "Комментарий — не длиннее 300 символов");
        }
    }
}
