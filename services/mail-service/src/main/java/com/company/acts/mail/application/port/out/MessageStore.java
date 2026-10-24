package com.company.acts.mail.application.port.out;

import com.company.acts.mail.application.view.MessageView;
import com.company.acts.mail.domain.MessageStatus;
import com.company.acts.mail.domain.SendMode;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Письма и вложения. */
public interface MessageStore {

    record Message(UUID id, UUID templateId, String subject, String body, List<String> to, List<String> cc,
                   List<String> bcc, String replyTo, List<UUID> actIds, List<String> actNumbers, SendMode mode,
                   MessageStatus status, int attempts, Instant nextAttemptAt, String error, Instant createdAt,
                   String createdBy, Instant sentAt) {
    }

    record Attachment(String fileName, String contentType, byte[] content) {
    }

    void save(Message message, List<Attachment> attachments);

    Optional<Message> find(UUID id);

    List<Attachment> attachments(UUID messageId);

    /** Письма, которым пора уходить; строки блокируются до конца транзакции. */
    List<Message> lockDue(Instant now, int limit);

    void markSent(UUID id, Instant at);

    void markAttempt(UUID id, int attempts, Instant nextAttemptAt, String error);

    /** Журнал: последние письма, при actId — только с этим актом. */
    List<MessageView> journal(UUID actId, int page, int size);

    long count(UUID actId);
}
