package com.company.acts.mail.domain;

import java.time.Duration;
import java.time.Instant;

/**
 * Повторы отправки при ошибке SMTP: через 1, 5, 15 и 60 минут; после пятой неудачной попытки письмо
 * помечается FAILED и показывается в журнале с текстом ошибки.
 */
public final class RetryPolicy {

    public static final int MAX_ATTEMPTS = 5;
    private static final Duration[] DELAYS = {Duration.ofMinutes(1), Duration.ofMinutes(5), Duration.ofMinutes(15),
        Duration.ofMinutes(60)};

    private RetryPolicy() {
    }

    /** Когда пробовать снова после неудачной попытки номер attempts (1…); null — больше не пробовать. */
    public static Instant nextAttempt(int attempts, Instant now) {
        if (attempts >= MAX_ATTEMPTS) {
            return null;
        }
        return now.plus(DELAYS[Math.min(attempts, DELAYS.length) - 1]);
    }
}
