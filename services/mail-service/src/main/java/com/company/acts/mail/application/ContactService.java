package com.company.acts.mail.application;

import com.company.acts.common.core.BusinessRuleException;
import com.company.acts.common.core.ConflictException;
import com.company.acts.common.core.NotFoundException;
import com.company.acts.common.core.Strings;
import com.company.acts.common.core.Uuid7;
import com.company.acts.common.importing.ImportColumn;
import com.company.acts.common.importing.ImportResult;
import com.company.acts.common.importing.ImportRow;
import com.company.acts.common.importing.TabularFileReader;
import com.company.acts.mail.application.port.out.ContactDirectory;
import com.company.acts.mail.application.port.out.ContactDirectory.Contact;
import com.company.acts.mail.domain.EmailAddresses;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Адресная книга (раздел 10 SPEC.md): контакты, группы, привязка к организации-заказчику («получатели по
 * умолчанию»), импорт и экспорт CSV. Добавлять контакты могут все; изменять группы — руководитель и администратор.
 */
@Service
public class ContactService {

    public record ContactCommand(String fullName, String email, UUID organizationId, String organizationName,
                                 String position, String notes, Boolean defaultForOrganization, Boolean active) {
    }

    static final List<ImportColumn> COLUMNS = List.of(
            ImportColumn.required("email", "Адрес почты", "Email", "E-mail", "Почта", "Адрес"),
            ImportColumn.optional("fullName", "ФИО", "ФИО", "Имя", "Контакт"),
            ImportColumn.optional("organization", "Организация (текстом)", "Организация", "Компания"),
            ImportColumn.optional("position", "Должность", "Должность"),
            ImportColumn.optional("notes", "Примечание", "Примечание", "Комментарий"));

    private final ContactDirectory contacts;

    public ContactService(ContactDirectory contacts) {
        this.contacts = contacts;
    }

    @Transactional(readOnly = true)
    public List<Contact> search(String q, UUID organizationId, boolean includeInactive) {
        return contacts.search(Strings.trimToNull(q), organizationId, includeInactive, 50);
    }

    @Transactional
    public Contact create(ContactCommand c) {
        String email = email(c.email());
        contacts.findByEmail(email).ifPresent(x -> {
            throw new ConflictException("Контакт с адресом " + email + " уже есть: " + label(x));
        });
        return contacts.save(toContact(Uuid7.next(), c, email));
    }

    @Transactional
    public Contact update(UUID id, ContactCommand c) {
        contacts.find(id).orElseThrow(() -> NotFoundException.of("Контакт", id));
        String email = email(c.email());
        contacts.findByEmail(email).filter(x -> !x.id().equals(id)).ifPresent(x -> {
            throw new ConflictException("Адрес " + email + " уже у контакта " + label(x));
        });
        return contacts.save(toContact(id, c, email));
    }

    @Transactional(readOnly = true)
    public List<ContactDirectory.Group> groups() {
        return contacts.groups();
    }

    @PreAuthorize("hasAnyRole('ADMIN', 'DEPARTMENT_HEAD')")
    @Transactional
    public ContactDirectory.Group saveGroup(UUID id, String name, List<UUID> memberIds) {
        String title = Strings.trimToNull(name);
        if (title == null) {
            throw new BusinessRuleException("name", "Укажите название группы");
        }
        contacts.groups().stream().filter(g -> g.name().equalsIgnoreCase(title) && !g.id().equals(id)).findFirst()
                .ifPresent(g -> {
                    throw new ConflictException("Группа «" + g.name() + "» уже есть");
                });
        List<UUID> members = memberIds == null ? List.of() : memberIds.stream().distinct().toList();
        for (UUID m : members) {
            contacts.find(m).orElseThrow(() -> new BusinessRuleException("memberIds", "Контакт не найден: " + m));
        }
        return contacts.saveGroup(new ContactDirectory.Group(id != null ? id : Uuid7.next(), title, members));
    }

    @PreAuthorize("hasAnyRole('ADMIN', 'DEPARTMENT_HEAD')")
    @Transactional
    public void deleteGroup(UUID id) {
        contacts.deleteGroup(id);
    }

    /** Импорт CSV или Excel: новые адреса добавляются, существующие — обновляются. */
    @Transactional
    public ImportResult importFile(String filename, byte[] content) {
        ImportResult result = new ImportResult();
        for (ImportRow row : TabularFileReader.read(filename, content, COLUMNS)) {
            try {
                String email = email(row.text("email"));
                var existing = contacts.findByEmail(email);
                ContactCommand c = new ContactCommand(row.text("fullName"), email, null, row.text("organization"),
                        row.text("position"), row.text("notes"), false, true);
                if (existing.isPresent()) {
                    Contact x = existing.get();
                    contacts.save(new Contact(x.id(), first(c.fullName(), x.fullName()), email, x.organizationId(),
                            first(c.organizationName(), x.organizationName()), first(c.position(), x.position()),
                            first(c.notes(), x.notes()), x.defaultForOrganization(), true));
                    result.updated();
                } else {
                    contacts.save(toContact(Uuid7.next(), c, email));
                    result.created();
                }
            } catch (BusinessRuleException e) {
                result.error(row.rowNumber(), e.getMessage());
            }
        }
        return result;
    }

    /** Экспорт активных контактов в CSV (UTF-8 с BOM, разделитель «;» — открывается в Excel). */
    @Transactional(readOnly = true)
    public byte[] exportCsv() {
        StringBuilder sb = new StringBuilder("\uFEFFФИО;Email;Организация;Должность;Примечание\r\n");
        for (Contact c : contacts.search(null, null, false, 100_000)) {
            sb.append(csv(c.fullName())).append(';').append(csv(c.email())).append(';')
                    .append(csv(c.organizationName())).append(';').append(csv(c.position())).append(';')
                    .append(csv(c.notes())).append("\r\n");
        }
        return sb.toString().getBytes(StandardCharsets.UTF_8);
    }

    private static Contact toContact(UUID id, ContactCommand c, String email) {
        return new Contact(id, Strings.trimToNull(c.fullName()), email, c.organizationId(),
                Strings.trimToNull(c.organizationName()), Strings.trimToNull(c.position()), Strings.trimToNull(c.notes()),
                Boolean.TRUE.equals(c.defaultForOrganization()) && c.organizationId() != null,
                c.active() == null || c.active());
    }

    private static String email(String raw) {
        if (!EmailAddresses.isValid(raw)) {
            throw new BusinessRuleException("email", "Неверный адрес почты" + (raw == null ? "" : ": " + raw.strip()));
        }
        return raw.strip();
    }

    private static String label(Contact c) {
        return c.fullName() != null ? c.fullName() : c.email();
    }

    private static String first(String a, String b) {
        return a != null ? a : b;
    }

    private static String csv(String value) {
        if (value == null) {
            return "";
        }
        return value.contains(";") || value.contains("\"") || value.contains("\n")
                ? "\"" + value.replace("\"", "\"\"") + "\"" : value;
    }
}
