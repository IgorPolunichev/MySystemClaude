package com.company.acts.directory.domain;

import com.company.acts.common.core.BusinessRuleException;
import com.company.acts.common.core.Strings;
import java.util.Objects;
import java.util.UUID;

/** Тип техники: карьерный самосвал, экскаватор, легковой автомобиль и т. д. */
public record EquipmentType(UUID id, Long version, String name) {

    public EquipmentType {
        Objects.requireNonNull(id, "id");
        name = Strings.trimToNull(name);
        if (name == null) {
            throw new BusinessRuleException("name", "Укажите название типа техники");
        }
    }
}
