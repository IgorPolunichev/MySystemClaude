package com.company.acts.mail.adapter.in.web;

import com.company.acts.common.importing.ImportResult;
import com.company.acts.mail.application.ContactService;
import com.company.acts.mail.application.MailService;
import com.company.acts.mail.application.TemplateService;
import com.company.acts.mail.application.port.out.ContactDirectory;
import com.company.acts.mail.application.port.out.TemplateStore;
import com.company.acts.mail.application.view.MessageView;
import com.company.acts.mail.domain.SendMode;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/** Письма по актам, адресная книга, шаблоны (раздел 10 SPEC.md). */
@RestController
class MailController {

    record PreviewRequest(@NotNull @Size(min = 1, max = 50) List<UUID> actIds, UUID templateId) {
    }

    record SendRequest(@NotNull @Size(min = 1, max = 50) List<UUID> actIds, UUID templateId, List<String> to,
                       List<String> cc, List<String> bcc, @Size(max = 1000) String subject, String body,
                       SendMode mode, boolean zip) {
    }

    record JournalPage(List<MessageView> items, int page, int size, long totalElements) {
    }

    record GroupRequest(String name, List<UUID> memberIds) {
    }

    record GroupResponse(UUID id, String name, List<UUID> memberIds, List<String> emails) {
    }

    record TemplateRequest(String name, String subject, String body, boolean isDefault) {
    }

    private final MailService mail;
    private final ContactService contacts;
    private final TemplateService templates;

    MailController(MailService mail, ContactService contacts, TemplateService templates) {
        this.mail = mail;
        this.contacts = contacts;
        this.templates = templates;
    }

    // ---------- Письма ----------

    @PostMapping("/api/v1/mail/preview")
    MailService.Preview preview(@jakarta.validation.Valid @RequestBody PreviewRequest r) {
        return mail.preview(r.actIds(), r.templateId());
    }

    @PostMapping("/api/v1/mail/messages")
    @ResponseStatus(HttpStatus.CREATED)
    MessageView send(@jakarta.validation.Valid @RequestBody SendRequest r) {
        return mail.send(new MailService.SendCommand(r.actIds(), r.templateId(), r.to(), r.cc(), r.bcc(), r.subject(),
                r.body(), r.mode(), r.zip()));
    }

    @GetMapping("/api/v1/mail/messages")
    JournalPage journal(@RequestParam(required = false) UUID actId, @RequestParam(defaultValue = "0") int page,
                        @RequestParam(defaultValue = "20") int size) {
        return new JournalPage(mail.journal(actId, page, size), page, size, mail.journalCount(actId));
    }

    @GetMapping("/api/v1/mail/messages/{id}/eml")
    ResponseEntity<byte[]> eml(@PathVariable UUID id) {
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType("message/rfc822"))
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                        .filename("Письмо с актами.eml", StandardCharsets.UTF_8).build().toString())
                .body(mail.eml(id));
    }

    // ---------- Адресная книга ----------

    @GetMapping("/api/v1/contacts")
    List<ContactDirectory.Contact> contacts(@RequestParam(required = false) String q,
                                            @RequestParam(required = false) UUID organizationId,
                                            @RequestParam(defaultValue = "false") boolean includeInactive) {
        return contacts.search(q, organizationId, includeInactive);
    }

    @PostMapping("/api/v1/contacts")
    @ResponseStatus(HttpStatus.CREATED)
    ContactDirectory.Contact createContact(@RequestBody ContactService.ContactCommand c) {
        return contacts.create(c);
    }

    @PutMapping("/api/v1/contacts/{id}")
    ContactDirectory.Contact updateContact(@PathVariable UUID id, @RequestBody ContactService.ContactCommand c) {
        return contacts.update(id, c);
    }

    @PostMapping("/api/v1/contacts/import")
    ImportResult importContacts(@RequestParam("file") MultipartFile file) throws IOException {
        return contacts.importFile(file.getOriginalFilename(), file.getBytes());
    }

    @GetMapping("/api/v1/contacts/export")
    ResponseEntity<byte[]> exportContacts() {
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType("text/csv; charset=UTF-8"))
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                        .filename("Адресная книга.csv", StandardCharsets.UTF_8).build().toString())
                .body(contacts.exportCsv());
    }

    @GetMapping("/api/v1/contact-groups")
    List<GroupResponse> groups() {
        return contacts.groups().stream().map(this::group).toList();
    }

    @PostMapping("/api/v1/contact-groups")
    @ResponseStatus(HttpStatus.CREATED)
    GroupResponse createGroup(@RequestBody GroupRequest r) {
        return group(contacts.saveGroup(null, r.name(), r.memberIds()));
    }

    @PutMapping("/api/v1/contact-groups/{id}")
    GroupResponse updateGroup(@PathVariable UUID id, @RequestBody GroupRequest r) {
        return group(contacts.saveGroup(id, r.name(), r.memberIds()));
    }

    private GroupResponse group(ContactDirectory.Group g) {
        return new GroupResponse(g.id(), g.name(), g.memberIds(), contacts.emails(g));
    }

    @DeleteMapping("/api/v1/contact-groups/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void deleteGroup(@PathVariable UUID id) {
        contacts.deleteGroup(id);
    }

    // ---------- Шаблоны ----------

    @GetMapping("/api/v1/email-templates")
    List<TemplateStore.Template> templates() {
        return templates.list();
    }

    @PostMapping("/api/v1/email-templates")
    @ResponseStatus(HttpStatus.CREATED)
    TemplateStore.Template createTemplate(@RequestBody TemplateRequest r) {
        return templates.save(null, r.name(), r.subject(), r.body(), r.isDefault());
    }

    @PutMapping("/api/v1/email-templates/{id}")
    TemplateStore.Template updateTemplate(@PathVariable UUID id, @RequestBody TemplateRequest r) {
        return templates.save(id, r.name(), r.subject(), r.body(), r.isDefault());
    }

    @DeleteMapping("/api/v1/email-templates/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void deleteTemplate(@PathVariable UUID id) {
        templates.delete(id);
    }
}
