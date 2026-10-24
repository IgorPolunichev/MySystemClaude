package com.company.acts.mail.application.port.out;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Адресная книга (порт, который позже можно вынести в общий сервис уведомлений — раздел 10 SPEC.md). */
public interface ContactDirectory {

    record Contact(UUID id, String fullName, String email, UUID organizationId, String organizationName,
                   String position, String notes, boolean defaultForOrganization, boolean active) {
    }

    record Group(UUID id, String name, List<UUID> memberIds) {
    }

    List<Contact> search(String q, UUID organizationId, boolean includeInactive, int limit);

    Optional<Contact> find(UUID id);

    Optional<Contact> findByEmail(String email);

    /** Активные контакты «по умолчанию» для указанных организаций-заказчиков. */
    List<Contact> defaultsFor(Collection<UUID> organizationIds);

    Contact save(Contact contact);

    List<Group> groups();

    Optional<Group> group(UUID id);

    Group saveGroup(Group group);

    void deleteGroup(UUID id);
}
