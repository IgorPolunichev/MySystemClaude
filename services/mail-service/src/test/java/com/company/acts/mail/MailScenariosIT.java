package com.company.acts.mail;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.company.acts.common.core.BusinessRuleException;
import com.company.acts.common.core.ConflictException;
import com.company.acts.common.test.PostgresIntegrationTest;
import com.company.acts.mail.application.ContactService;
import com.company.acts.mail.application.DeliveryService;
import com.company.acts.mail.application.MailService;
import com.company.acts.mail.application.TemplateService;
import com.company.acts.mail.application.port.out.ActSource;
import com.company.acts.mail.application.port.out.AttachmentSource;
import com.company.acts.mail.application.view.MessageView;
import com.company.acts.mail.domain.MessageStatus;
import com.company.acts.mail.domain.SendMode;
import jakarta.mail.Address;
import jakarta.mail.internet.MimeMessage;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mail.MailSendException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * Письма на реальном PostgreSQL, FreeMarker и JavaMail; подменены только SMTP-сервер (перехват писем), акты и
 * Excel-файлы актов.
 */
@SpringBootTest(properties = "app.mail.delivery-enabled=false")
class MailScenariosIT extends PostgresIntegrationTest {

    static final UUID ORG = UUID.randomUUID();
    static final Map<UUID, ActSource.ActInfo> ACTS = new ConcurrentHashMap<>();
    static final Map<UUID, Integer> SIZES = new ConcurrentHashMap<>();

    /** SMTP-сервер для тестов: сохраняет письма или отказывает, если failing = true. */
    static class CapturingSender extends JavaMailSenderImpl {
        final List<MimeMessage> sent = new ArrayList<>();
        volatile boolean failing;

        @Override
        protected void doSend(MimeMessage[] messages, Object[] originals) {
            if (failing) {
                throw new MailSendException("SMTP недоступен");
            }
            sent.addAll(Arrays.asList(messages));
        }
    }

    @TestConfiguration
    static class Fakes {
        @Bean
        @Primary
        CapturingSender capturingSender() {
            return new CapturingSender();
        }

        @Bean
        @Primary
        ActSource fakeActs() {
            return ACTS::get;
        }

        @Bean
        @Primary
        AttachmentSource fakeWorkbooks() {
            return id -> new AttachmentSource.File(ACTS.get(id).number() + ".xlsx",
                    "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                    new byte[SIZES.getOrDefault(id, 2048)]);
        }
    }

    @Autowired
    MailService mail;
    @Autowired
    DeliveryService delivery;
    @Autowired
    ContactService contacts;
    @Autowired
    TemplateService templates;
    @Autowired
    JavaMailSender sender;
    @Autowired
    JdbcTemplate jdbc;

    UUID closed;
    UUID open;

    @BeforeEach
    void setUp() {
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken("engineer", "n/a",
                List.of(new SimpleGrantedAuthority("ROLE_ADMIN"))));
        closed = UUID.randomUUID();
        open = UUID.randomUUID();
        ACTS.put(closed, new ActSource.ActInfo(closed, "АВР-2026-000101", "CLOSED", ORG, "АО «Северный ГОК» & Ко",
                "05.10.2026 — 07.10.2026", "CAT 777G, гар. № 45", "ТО-2000", new BigDecimal("21.5"), null));
        ACTS.put(open, new ActSource.ActInfo(open, "АВР-2026-000102", "OPEN", ORG, "АО «Северный ГОК» & Ко",
                "09.10.2026", "Komatsu PC2000", "Замена ГМП", new BigDecimal("8"), Instant.now()));
        // письма, оставшиеся в очереди от других тестов, не должны уходить в этом
        jdbc.update("update email_message set status = 'SENT', next_attempt_at = null where status = 'QUEUED'");
        ((CapturingSender) sender).sent.clear();
        ((CapturingSender) sender).failing = false;
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void previewUsesDefaultTemplateRecipientsAndWarnings() {
        contacts.create(new ContactService.ContactCommand("Сидоров А. В.", "mechanic-" + UUID.randomUUID() + "@gok.ru", ORG,
                "АО «Северный ГОК»", "Механик", null, true, true));
        MailService.Preview p = mail.preview(List.of(closed, open), null);
        assertThat(p.subject()).isEqualTo("Акты выполненных работ № АВР-2026-000101, АВР-2026-000102");
        assertThat(p.body()).contains("<table").contains("CAT 777G, гар. № 45").contains("29,5");
        assertThat(p.defaultTo()).anyMatch(e -> e.startsWith("mechanic-"));
        assertThat(p.attachmentNames()).containsExactly("АВР-2026-000101.xlsx", "АВР-2026-000102.xlsx");
        assertThat(p.warnings()).anyMatch(w -> w.contains("уже отправлялся")).anyMatch(w -> w.contains("не закрыт"));
    }

    @Test
    void smtpMessageIsQueuedDeliveredAndPublishesEmailSent() throws Exception {
        MessageView queued = mail.send(new MailService.SendCommand(List.of(closed, open), null,
                List.of("buh@gok.ru", "BUH@gok.ru"), List.of(), null, null, null, SendMode.SMTP, false));
        assertThat(queued.status()).isEqualTo(MessageStatus.QUEUED);
        assertThat(queued.to()).containsExactly("buh@gok.ru");

        assertThat(delivery.deliverDue()).isEqualTo(1);
        CapturingSender capture = (CapturingSender) sender;
        assertThat(capture.sent).hasSize(1);
        MimeMessage message = capture.sent.get(0);
        assertThat(message.getSubject()).startsWith("Акты выполненных работ №");
        Address[] recipients = message.getAllRecipients();
        assertThat(recipients).hasSize(1);
        String raw = raw(message);
        assertThat(raw.split("Content-Disposition: attachment", -1)).hasSize(3);

        assertThat(mail.journal(closed, 0, 10)).first().satisfies(m -> assertThat(m.status()).isEqualTo(MessageStatus.SENT));
        String payload = jdbc.queryForObject("select payload::text from outbox where aggregate_id = ? and event_type = 'EmailSent'",
                String.class, queued.id());
        assertThat(payload).contains(closed.toString()).contains(open.toString()).contains("\"mode\":\"SMTP\"");
    }

    @Test
    void failedSmtpIsRetriedThenMarkedFailed() {
        CapturingSender capture = (CapturingSender) sender;
        capture.failing = true;
        MessageView m = mail.send(new MailService.SendCommand(List.of(closed), null, List.of("a@b.ru"), null, null,
                "Тема", "<p>Текст</p>", SendMode.SMTP, false));
        for (int i = 1; i <= 5; i++) {
            jdbc.update("update email_message set next_attempt_at = now() - interval '1 second' where id = ?"
                    + " and status = 'QUEUED'", m.id());
            delivery.deliverDue();
        }
        MessageView failed = mail.journal(closed, 0, 50).stream().filter(x -> x.id().equals(m.id())).findFirst()
                .orElseThrow();
        assertThat(failed.status()).isEqualTo(MessageStatus.FAILED);
        assertThat(failed.attempts()).isEqualTo(5);
        assertThat(failed.error()).contains("SMTP недоступен");
    }

    @Test
    void emlForOutlookAndZipForLargeAttachments() {
        MessageView eml = mail.send(new MailService.SendCommand(List.of(closed), null, List.of("a@b.ru"), null, null,
                null, null, SendMode.EML, false));
        assertThat(eml.status()).isEqualTo(MessageStatus.EML);
        String text = new String(mail.eml(eml.id()), StandardCharsets.UTF_8);
        assertThat(text).contains("X-Unsent: 1").contains("Subject:");

        SIZES.put(closed, 11 * 1024 * 1024);
        SIZES.put(open, 11 * 1024 * 1024);
        assertThatThrownBy(() -> mail.send(new MailService.SendCommand(List.of(closed, open), null, List.of("a@b.ru"),
                null, null, null, null, SendMode.SMTP, false)))
                .isInstanceOf(BusinessRuleException.class).hasMessageContaining("ZIP");
        MessageView zipped = mail.send(new MailService.SendCommand(List.of(closed, open), null, List.of("a@b.ru"), null,
                null, null, null, SendMode.SMTP, true));
        assertThat(jdbc.queryForList("select file_name from email_attachment where message_id = ?", String.class,
                zipped.id())).containsExactly("Акты.zip");
        SIZES.clear();

        assertThatThrownBy(() -> mail.send(new MailService.SendCommand(List.of(closed), null, List.of("не адрес"), null,
                null, null, null, SendMode.SMTP, false))).isInstanceOf(BusinessRuleException.class);
    }

    @Test
    void contactsAndTemplates() {
        String email = "c-" + UUID.randomUUID() + "@example.ru";
        contacts.create(new ContactService.ContactCommand("Петрова Е. И.", email, null, null, null, null, false, true));
        assertThatThrownBy(() -> contacts.create(new ContactService.ContactCommand(null, email.toUpperCase(), null, null,
                null, null, false, true))).isInstanceOf(ConflictException.class);
        String csv = "Email;ФИО;Организация\n" + email + ";Петрова Елена;ООО «Тест»\nnew-" + UUID.randomUUID()
                + "@example.ru;Новый;\nплохой адрес;Ошибка;\n";
        var result = contacts.importFile("contacts.csv", csv.getBytes(StandardCharsets.UTF_8));
        assertThat(result.getUpdated()).isEqualTo(1);
        assertThat(result.getCreated()).isEqualTo(1);
        assertThat(result.getErrors()).hasSize(1);
        assertThat(new String(contacts.exportCsv(), StandardCharsets.UTF_8)).contains(email).contains("Петрова Елена");

        assertThatThrownBy(() -> templates.save(null, "Сломанный", "Тема ${", "<p>x</p>", false))
                .isInstanceOf(BusinessRuleException.class);
        assertThatThrownBy(() -> templates.save(null, "Неизвестная переменная", "Тема", "<p>${нетТакой}</p>", false))
                .isInstanceOf(BusinessRuleException.class);
        var custom = templates.save(null, "Коротко", "Акты: ${acts?size}", "<p>Направляем ${acts?size} акт(а).</p>", false);
        assertThat(mail.preview(List.of(closed, open), custom.id()).subject()).isEqualTo("Акты: 2");
        // значения экранируются в HTML: «&» в названии заказчика не ломает разметку
        var org = templates.save(null, "Заказчик", "Т", "<p><#list acts as a>${a.organization}</#list></p>", false);
        assertThat(mail.preview(List.of(closed), org.id()).body()).contains("&amp; Ко");
    }

    private static String raw(MimeMessage message) throws Exception {
        java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
        message.writeTo(out);
        return out.toString(StandardCharsets.UTF_8);
    }
}
