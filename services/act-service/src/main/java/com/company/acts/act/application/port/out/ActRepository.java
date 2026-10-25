package com.company.acts.act.application.port.out;

import com.company.acts.act.application.view.ActSearchQuery;
import com.company.acts.act.application.view.ActSummary;
import com.company.acts.act.domain.WorkAct;
import com.company.acts.common.core.PageQuery;
import com.company.acts.common.core.PageResult;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ActRepository {

    Optional<WorkAct> findById(UUID id);

    /** Время создания и последнего изменения акта. */
    /** [создан, изменён, последняя отправка письмом]. */
    Optional<Instant[]> timestamps(UUID id);

    WorkAct save(WorkAct act);

    /**
     * Акты пользователя для офлайн-работы (раздел 17.3 SPEC.md): где он участник — черновики и открытые акты
     * целиком плюс любые изменённые после указанного момента; не больше limit, новые первыми.
     */
    List<UUID> idsForDevice(String userId, Instant changedSince, int limit);

    /** Физическое удаление акта вместе со всем содержимым. */
    void delete(UUID id);

    /** Удалить аннулированные акты, не изменявшиеся с указанного момента; возвращает их число. */
    int deleteCancelledBefore(Instant before);

    /** Пересчитать сохранённые итоги акта (часы, период, ФИО участников) для реестра. */
    void refreshAggregates(UUID id);

    PageResult<ActSummary> search(ActSearchQuery query, PageQuery page);

    List<String> shortDescriptions(String prefix, int limit);
}
