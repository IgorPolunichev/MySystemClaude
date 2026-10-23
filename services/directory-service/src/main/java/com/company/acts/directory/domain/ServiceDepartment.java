package com.company.acts.directory.domain;

import com.company.acts.common.core.BusinessRuleException;
import com.company.acts.common.core.Strings;
import java.util.Objects;
import java.util.UUID;

/** Отдел сервисной компании; для него задаётся норма часов. */
public record ServiceDepartment(UUID id, Long version, String name) {

    public ServiceDepartment {
        Objects.requireNonNull(id, "id");
        name = Strings.trimToNull(name);
        if (name == null) {
            throw new BusinessRuleException("name", "Укажите название отдела");
        }
    }
}
