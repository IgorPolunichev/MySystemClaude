package com.company.acts.mail.application.port.out;

import java.util.List;

/** Отправка через SMTP и формирование .eml (порт MessageSender из раздела 10 SPEC.md). */
public interface MailTransport {

    record OutgoingMail(String subject, String htmlBody, List<String> to, List<String> cc, List<String> bcc,
                        String replyTo, List<MessageStore.Attachment> attachments) {
    }

    void send(OutgoingMail mail);

    byte[] toEml(OutgoingMail mail);
}
