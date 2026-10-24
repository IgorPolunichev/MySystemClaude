package com.company.acts.mail.application;

import com.company.acts.common.core.BusinessRuleException;
import com.company.acts.common.core.ConflictException;
import com.company.acts.common.core.NotFoundException;
import com.company.acts.common.core.Strings;
import com.company.acts.common.core.Uuid7;
import com.company.acts.mail.application.port.out.CurrentUserProvider;
import com.company.acts.mail.application.port.out.TemplateRenderer;
import com.company.acts.mail.application.port.out.TemplateStore;
import com.company.acts.mail.application.port.out.TemplateStore.Template;
import java.util.List;
import java.util.UUID;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Шаблоны писем на FreeMarker; создаёт и изменяет администратор. Шаблон проверяется на тестовых данных. */
@Service
public class TemplateService {

    private final TemplateStore templates;
    private final TemplateRenderer renderer;
    private final CurrentUserProvider users;

    public TemplateService(TemplateStore templates, TemplateRenderer renderer, CurrentUserProvider users) {
        this.templates = templates;
        this.renderer = renderer;
        this.users = users;
    }

    @Transactional(readOnly = true)
    public List<Template> list() {
        return templates.list();
    }

    @PreAuthorize("hasRole('ADMIN')")
    @Transactional
    public Template save(UUID id, String name, String subject, String body, boolean isDefault) {
        if (id != null) {
            templates.find(id).orElseThrow(() -> NotFoundException.of("Шаблон", id));
        }
        if (Strings.trimToNull(name) == null || Strings.trimToNull(subject) == null || Strings.trimToNull(body) == null) {
            throw new BusinessRuleException("name", "Заполните название, тему и текст шаблона");
        }
        // Шаблон с ошибкой не сохраняем: проверяем на тестовом акте.
        var model = MailModel.sample(users.current());
        try {
            renderer.renderText(subject, model);
            renderer.renderHtml(body, model);
        } catch (IllegalArgumentException e) {
            throw new BusinessRuleException("body", "Ошибка в шаблоне: " + e.getMessage());
        }
        return templates.save(new Template(id != null ? id : Uuid7.next(), name.strip(), subject.strip(), body,
                isDefault), users.current().username());
    }

    @PreAuthorize("hasRole('ADMIN')")
    @Transactional
    public void delete(UUID id) {
        Template t = templates.find(id).orElseThrow(() -> NotFoundException.of("Шаблон", id));
        if (t.isDefault()) {
            throw new ConflictException("Шаблон по умолчанию удалить нельзя — сначала назначьте другой");
        }
        templates.delete(id);
    }
}
