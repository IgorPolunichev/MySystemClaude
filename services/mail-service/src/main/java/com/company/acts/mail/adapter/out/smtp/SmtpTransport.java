package com.company.acts.mail.adapter.out.smtp;

import com.company.acts.mail.application.port.out.MailTransport;
import com.company.acts.mail.application.port.out.MessageStore;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Component;

/**
 * SMTP от общего ящика ({@code MAIL_FROM}) с адресом пользователя в Reply-To; .eml — то же письмо с заголовком
 * {@code X-Unsent: 1}, чтобы Outlook открыл его как черновик для отправки из своего ящика.
 */
@Component
class SmtpTransport implements MailTransport {

    private final JavaMailSender sender;
    private final String from;

    SmtpTransport(JavaMailSender sender, @Value("${app.mail.from:acts@company.local}") String from) {
        this.sender = sender;
        this.from = from;
    }

    @Override
    public void send(OutgoingMail mail) {
        sender.send(build(mail, false));
    }

    @Override
    public byte[] toEml(OutgoingMail mail) {
        try {
            MimeMessage message = build(mail, true);
            message.saveChanges();
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            message.writeTo(out);
            return out.toByteArray();
        } catch (MessagingException | IOException e) {
            throw new IllegalStateException("Не удалось сформировать .eml: " + e.getMessage(), e);
        }
    }

    private MimeMessage build(OutgoingMail mail, boolean unsent) {
        try {
            MimeMessage message = sender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
            if (!unsent) {
                helper.setFrom(from);
            }
            if (mail.replyTo() != null) {
                helper.setReplyTo(mail.replyTo());
            }
            helper.setTo(mail.to().toArray(String[]::new));
            if (!mail.cc().isEmpty()) {
                helper.setCc(mail.cc().toArray(String[]::new));
            }
            if (!mail.bcc().isEmpty()) {
                helper.setBcc(mail.bcc().toArray(String[]::new));
            }
            helper.setSubject(mail.subject());
            helper.setText(mail.htmlBody(), true);
            for (MessageStore.Attachment a : mail.attachments()) {
                helper.addAttachment(a.fileName(), new ByteArrayResource(a.content()), a.contentType());
            }
            if (unsent) {
                message.setHeader("X-Unsent", "1");
            }
            return message;
        } catch (MessagingException e) {
            throw new IllegalStateException("Не удалось собрать письмо: " + e.getMessage(), e);
        }
    }
}
