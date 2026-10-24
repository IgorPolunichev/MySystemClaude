package com.company.acts.mail.application.port.out;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/** Данные акта для письма (с правами пользователя). */
public interface ActSource {

    record ActInfo(UUID id, String number, String status, UUID organizationId, String organizationName,
                   String period, String equipment, String shortDescription, BigDecimal totalHours,
                   Instant lastSentAt) {
    }

    ActInfo act(UUID id);
}
