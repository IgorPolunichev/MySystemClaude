package com.company.acts.act.adapter.in.scheduler;

import com.company.acts.act.application.ActService;
import java.time.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Фоновая очистка аннулированных актов: раз в сутки удаляются акты, аннулированные и не изменявшиеся дольше
 * {@code app.acts.purge-cancelled-after-days} дней (по умолчанию 30). Отключается {@code app.acts.purge-enabled=false}.
 */
@Component
@ConditionalOnProperty(name = "app.acts.purge-enabled", havingValue = "true", matchIfMissing = true)
class CancelledActsPurge {

    private static final Logger log = LoggerFactory.getLogger(CancelledActsPurge.class);

    private final ActService acts;
    private final Duration olderThan;

    CancelledActsPurge(ActService acts, @Value("${app.acts.purge-cancelled-after-days:30}") long days) {
        this.acts = acts;
        this.olderThan = Duration.ofDays(days);
    }

    @Scheduled(cron = "${app.acts.purge-cron:0 30 3 * * *}")
    void purge() {
        int deleted = acts.purgeCancelled(olderThan);
        if (deleted > 0) {
            log.info("Удалено аннулированных актов старше {} дн.: {}", olderThan.toDays(), deleted);
        }
    }
}
