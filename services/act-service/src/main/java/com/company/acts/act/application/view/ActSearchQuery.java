package com.company.acts.act.application.view;

import com.company.acts.act.domain.ActStatus;
import java.time.LocalDate;
import java.util.Set;
import java.util.UUID;

/**
 * Фильтры реестра актов. {@code from}/{@code to} — пересечение с периодом работ (у актов без записей
 * времени — дата создания). {@code participantUserId} — «мои акты».
 */
public record ActSearchQuery(String text, Set<ActStatus> statuses, UUID organizationId, UUID equipmentId,
                             UUID engineerId, String participantUserId, LocalDate from, LocalDate to) {
}
