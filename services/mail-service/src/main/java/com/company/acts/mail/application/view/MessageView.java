package com.company.acts.mail.application.view;

import com.company.acts.mail.domain.MessageStatus;
import com.company.acts.mail.domain.SendMode;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Строка журнала отправок. */
public record MessageView(UUID id, String subject, List<String> to, List<String> cc, List<UUID> actIds,
                          List<String> actNumbers, SendMode mode, MessageStatus status, int attempts, String error,
                          Instant createdAt, String createdBy, Instant sentAt) {
}
