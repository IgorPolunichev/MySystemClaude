package com.company.acts.directory.domain;

import com.company.acts.common.core.BusinessRuleException;
import com.company.acts.common.core.Strings;
import java.util.Objects;
import java.util.UUID;

/** Подразделение организации-заказчика; допускается вложенность (участок внутри цеха). */
public record Department(UUID id, Long version, UUID organizationId, UUID parentId, String name, String code) {

    public Department {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(organizationId, "organizationId");
        name = Strings.trimToNull(name);
        code = Strings.trimToNull(code);
        if (name == null) {
            throw new BusinessRuleException("name", "Укажите название подразделения");
        }
        if (id.equals(parentId)) {
            throw new BusinessRuleException("parentId", "Подразделение не может входить само в себя");
        }
    }
}
