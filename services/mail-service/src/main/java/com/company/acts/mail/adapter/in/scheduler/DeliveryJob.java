package com.company.acts.mail.adapter.in.scheduler;

import com.company.acts.mail.application.DeliveryService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Фоновая отправка очереди писем каждые 10 секунд. В тестах выключена ({@code app.mail.delivery-enabled=false}). */
@Component
@ConditionalOnProperty(name = "app.mail.delivery-enabled", havingValue = "true", matchIfMissing = true)
class DeliveryJob {

    private final DeliveryService delivery;

    DeliveryJob(DeliveryService delivery) {
        this.delivery = delivery;
    }

    @Scheduled(fixedDelayString = "${app.mail.delivery-interval-ms:10000}")
    void run() {
        delivery.deliverDue();
    }
}
