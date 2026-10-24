package com.company.acts.mail.application.port.out;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface TemplateStore {

    record Template(UUID id, String name, String subject, String body, boolean isDefault) {
    }

    List<Template> list();

    Optional<Template> find(UUID id);

    Optional<Template> findDefault();

    Template save(Template template, String user);

    void delete(UUID id);
}
