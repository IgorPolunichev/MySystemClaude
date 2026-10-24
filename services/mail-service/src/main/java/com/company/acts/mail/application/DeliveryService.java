package com.company.acts.mail.application;

import com.company.acts.mail.application.port.out.MailTransport;
import com.company.acts.mail.application.port.out.MessageStore;
import com.company.acts.mail.domain.RetryPolicy;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Фоновая отправка очереди писем через SMTP с повторами (раздел 10 SPEC.md). */
@Service
public class DeliveryService {

    private static final Logger log = LoggerFactory.getLogger(DeliveryService.class);
    private static final int BATCH = 10;

    private final MessageStore messages;
    private final MailTransport transport;
    private final MailService mail;
    private final Clock clock;

    public DeliveryService(MessageStore messages, MailTransport transport, MailService mail, Clock clock) {
        this.messages = messages;
        this.transport = transport;
        this.mail = mail;
        this.clock = clock;
    }

    /** Отправить письма, которым пора уходить; возвращает число успешно отправленных. */
    @Transactional
    public int deliverDue() {
        int sent = 0;
        Instant now = Instant.now(clock);
        List<MessageStore.Message> due = messages.lockDue(now, BATCH);
        for (MessageStore.Message m : due) {
            int attempt = m.attempts() + 1;
            try {
                transport.send(MailService.outgoing(m, messages.attachments(m.id())));
                messages.markSent(m.id(), now);
                mail.publishSent(m, now);
                sent++;
            } catch (RuntimeException e) {
                String error = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
                Instant next = RetryPolicy.nextAttempt(attempt, now);
                messages.markAttempt(m.id(), attempt, next, error.length() > 2000 ? error.substring(0, 2000) : error);
                log.warn("Письмо {} не отправлено (попытка {}): {}", m.id(), attempt, error);
            }
        }
        return sent;
    }
}
