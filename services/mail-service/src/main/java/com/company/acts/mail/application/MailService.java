package com.company.acts.mail.application;

import com.company.acts.common.core.BusinessRuleException;
import com.company.acts.common.core.NotFoundException;
import com.company.acts.common.core.Strings;
import com.company.acts.common.core.Uuid7;
import com.company.acts.mail.application.port.out.ActSource;
import com.company.acts.mail.application.port.out.AttachmentSource;
import com.company.acts.mail.application.port.out.ContactDirectory;
import com.company.acts.mail.application.port.out.CurrentUserProvider;
import com.company.acts.mail.application.port.out.EventPublisher;
import com.company.acts.mail.application.port.out.MailTransport;
import com.company.acts.mail.application.port.out.MessageStore;
import com.company.acts.mail.application.port.out.TemplateRenderer;
import com.company.acts.mail.application.port.out.TemplateStore;
import com.company.acts.mail.application.view.MessageView;
import com.company.acts.mail.domain.EmailAddresses;
import com.company.acts.mail.domain.MessageStatus;
import com.company.acts.mail.domain.SendMode;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Формирование писем по актам (раздел 10 SPEC.md): предпросмотр с подстановкой реальных данных и получателями
 * по умолчанию; отправка — в очередь (SMTP, фоновая отправка с повторами) или сразу в .eml для Outlook. Excel
 * каждого акта формируется в момент отправки с правами пользователя и хранится вместе с письмом.
 */
@Service
public class MailService {

    public static final int MAX_ACTS = 50;
    public static final long MAX_ATTACHMENTS_BYTES = 20L * 1024 * 1024;
    public static final String EMAIL_SENT = "EmailSent";

    public record Preview(String subject, String body, UUID templateId, List<String> defaultTo,
                          List<String> attachmentNames, List<String> warnings) {
    }

    public record SendCommand(List<UUID> actIds, UUID templateId, List<String> to, List<String> cc, List<String> bcc,
                              String subject, String body, SendMode mode, boolean zip) {
    }

    private final ActSource acts;
    private final AttachmentSource attachments;
    private final ContactDirectory contacts;
    private final TemplateStore templates;
    private final TemplateRenderer renderer;
    private final MessageStore messages;
    private final MailTransport transport;
    private final EventPublisher events;
    private final CurrentUserProvider users;
    private final Clock clock;

    public MailService(ActSource acts, AttachmentSource attachments, ContactDirectory contacts, TemplateStore templates,
                       TemplateRenderer renderer, MessageStore messages, MailTransport transport, EventPublisher events,
                       CurrentUserProvider users, Clock clock) {
        this.acts = acts;
        this.attachments = attachments;
        this.contacts = contacts;
        this.templates = templates;
        this.renderer = renderer;
        this.messages = messages;
        this.transport = transport;
        this.events = events;
        this.users = users;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public Preview preview(List<UUID> actIds, UUID templateId) {
        List<ActSource.ActInfo> list = load(actIds);
        TemplateStore.Template template = template(templateId);
        Map<String, Object> model = MailModel.of(list, users.current());
        List<String> warnings = new ArrayList<>();
        for (ActSource.ActInfo a : list) {
            if (a.lastSentAt() != null) {
                warnings.add("Акт " + a.number() + " уже отправлялся — письмо уйдёт повторно");
            }
            if (!"CLOSED".equals(a.status())) {
                warnings.add("Акт " + (a.number() != null ? a.number() : "без номера") + " ещё не закрыт");
            }
        }
        List<UUID> organizations = list.stream().map(ActSource.ActInfo::organizationId).filter(Objects::nonNull)
                .distinct().toList();
        List<String> defaultTo = organizations.isEmpty() ? List.of()
                : contacts.defaultsFor(organizations).stream().map(ContactDirectory.Contact::email).toList();
        return new Preview(renderer.renderText(template.subject(), model), renderer.renderHtml(template.body(), model),
                template.id(), defaultTo, list.stream().map(MailService::fileName).toList(), warnings);
    }

    @Transactional
    public MessageView send(SendCommand c) {
        List<ActSource.ActInfo> list = load(c.actIds());
        SendMode mode = c.mode() == null ? SendMode.SMTP : c.mode();
        List<String> to = EmailAddresses.normalize(c.to(), "to");
        List<String> cc = EmailAddresses.normalize(c.cc(), "cc");
        List<String> bcc = EmailAddresses.normalize(c.bcc(), "bcc");
        if (mode == SendMode.SMTP && to.isEmpty()) {
            throw new BusinessRuleException("to", "Укажите хотя бы одного получателя");
        }
        TemplateStore.Template template = template(c.templateId());
        Map<String, Object> model = MailModel.of(list, users.current());
        String subject = Strings.trimToNull(c.subject()) != null ? c.subject().strip()
                : renderer.renderText(template.subject(), model);
        String body = Strings.trimToNull(c.body()) != null ? c.body() : renderer.renderHtml(template.body(), model);
        if (subject.length() > 1000) {
            throw new BusinessRuleException("subject", "Тема — не длиннее 1000 символов");
        }

        List<MessageStore.Attachment> files = new ArrayList<>();
        for (ActSource.ActInfo a : list) {
            AttachmentSource.File f = attachments.actWorkbook(a.id());
            files.add(new MessageStore.Attachment(f.name(), f.contentType(), f.content()));
        }
        long size = files.stream().mapToLong(f -> f.content().length).sum();
        if (c.zip()) {
            files = List.of(zip(files));
        } else if (size > MAX_ATTACHMENTS_BYTES) {
            throw new BusinessRuleException("zip", "Вложения больше 20 МБ — отправьте их архивом ZIP");
        }

        CurrentUserProvider.CurrentUser user = users.current();
        Instant now = Instant.now(clock);
        MessageStore.Message message = new MessageStore.Message(Uuid7.next(), template.id(), subject, body, to, cc, bcc,
                user.email(), list.stream().map(ActSource.ActInfo::id).toList(),
                list.stream().map(a -> a.number() != null ? a.number() : "черновик").toList(), mode,
                mode == SendMode.SMTP ? MessageStatus.QUEUED : MessageStatus.EML, 0,
                mode == SendMode.SMTP ? now : null, null, now, user.username(), mode == SendMode.EML ? now : null);
        messages.save(message, files);
        if (mode == SendMode.EML) {
            publishSent(message, now);
        }
        return view(message);
    }

    /** Готовое письмо .eml с вложениями — открыть и отправить из своего Outlook. */
    @Transactional(readOnly = true)
    public byte[] eml(UUID messageId) {
        MessageStore.Message m = owned(messageId);
        return transport.toEml(outgoing(m, messages.attachments(m.id())));
    }

    @Transactional(readOnly = true)
    public List<MessageView> journal(UUID actId, int page, int size) {
        return messages.journal(actId, Math.max(0, page), Math.min(Math.max(1, size), 100));
    }

    @Transactional(readOnly = true)
    public long journalCount(UUID actId) {
        return messages.count(actId);
    }

    void publishSent(MessageStore.Message m, Instant at) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("messageId", m.id().toString());
        payload.put("actIds", m.actIds().stream().map(UUID::toString).toList());
        payload.put("mode", m.mode().name());
        payload.put("sentAt", at.toString());
        payload.put("sentBy", m.createdBy());
        payload.put("to", m.to());
        events.publish(EMAIL_SENT, m.id(), payload);
    }

    static MailTransport.OutgoingMail outgoing(MessageStore.Message m, List<MessageStore.Attachment> files) {
        return new MailTransport.OutgoingMail(m.subject(), m.body(), m.to(), m.cc(), m.bcc(), m.replyTo(), files);
    }

    static MessageView view(MessageStore.Message m) {
        return new MessageView(m.id(), m.subject(), m.to(), m.cc(), m.actIds(), m.actNumbers(), m.mode(), m.status(),
                m.attempts(), m.error(), m.createdAt(), m.createdBy(), m.sentAt());
    }

    private MessageStore.Message owned(UUID id) {
        MessageStore.Message m = messages.find(id).orElseThrow(() -> NotFoundException.of("Письмо", id));
        CurrentUserProvider.CurrentUser user = users.current();
        if (!user.isAdmin() && !user.username().equals(m.createdBy())) {
            throw new AccessDeniedException("Скачать письмо может его автор или администратор");
        }
        return m;
    }

    private List<ActSource.ActInfo> load(List<UUID> actIds) {
        if (actIds == null || actIds.isEmpty()) {
            throw new BusinessRuleException("actIds", "Выберите хотя бы один акт");
        }
        List<UUID> unique = new ArrayList<>(new LinkedHashSet<>(actIds));
        if (unique.size() > MAX_ACTS) {
            throw new BusinessRuleException("actIds", "Не больше " + MAX_ACTS + " актов в одном письме");
        }
        return unique.stream().map(acts::act).toList();
    }

    private TemplateStore.Template template(UUID id) {
        return id != null ? templates.find(id).orElseThrow(() -> NotFoundException.of("Шаблон", id))
                : templates.findDefault().orElseThrow(() -> new BusinessRuleException("templateId",
                "Нет шаблона по умолчанию — выберите шаблон"));
    }

    private static String fileName(ActSource.ActInfo a) {
        return (a.number() != null ? a.number() : "Черновик") + ".xlsx";
    }

    private static MessageStore.Attachment zip(List<MessageStore.Attachment> files) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(out, java.nio.charset.StandardCharsets.UTF_8)) {
            for (MessageStore.Attachment f : files) {
                zip.putNextEntry(new ZipEntry(f.fileName()));
                zip.write(f.content());
                zip.closeEntry();
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return new MessageStore.Attachment("Акты.zip", "application/zip", out.toByteArray());
    }
}
