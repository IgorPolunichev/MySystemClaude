package com.company.acts.act.application.view;

import com.company.acts.act.domain.ActStatus;
import java.time.LocalDate;
import java.util.Set;
import java.util.UUID;

/**
 * Фильтры реестра актов (раздел 8 SPEC.md).
 *
 * @param text              полнотекстовый запрос по всему акту (русская морфология) и по части номеров
 * @param from              начало периода: пересечение с периодом работ (у актов без табеля — дата создания)
 * @param participantUserId «мои акты» — где пользователь участвует
 * @param partNumber        часть каталожного номера запчасти: акты, где она использовалась
 * @param shortDescription  краткое описание — точное совпадение (выбор из ранее использованных)
 * @param signedScan        true — только с приложенным сканом подписанного акта, false — только без него
 */
public record ActSearchQuery(String text, Set<ActStatus> statuses, UUID organizationId, UUID equipmentId,
                             UUID engineerId, String participantUserId, LocalDate from, LocalDate to,
                             UUID siteId, String partNumber, String shortDescription, Boolean signedScan) {

    public ActSearchQuery(String text, Set<ActStatus> statuses, UUID organizationId, UUID equipmentId, UUID engineerId,
                          String participantUserId, LocalDate from, LocalDate to) {
        this(text, statuses, organizationId, equipmentId, engineerId, participantUserId, from, to, null, null, null,
                null);
    }

    public ActSearchQuery withParticipant(String userId) {
        return new ActSearchQuery(text, statuses, organizationId, equipmentId, engineerId, userId, from, to, siteId,
                partNumber, shortDescription, signedScan);
    }
}
