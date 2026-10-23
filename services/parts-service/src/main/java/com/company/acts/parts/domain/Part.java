package com.company.acts.parts.domain;

import com.company.acts.common.core.BusinessRuleException;
import com.company.acts.common.core.Strings;
import java.util.Objects;
import java.util.UUID;

/**
 * Позиция каталога запчастей. Каталожный номер хранится в верхнем регистре без пробелов по краям;
 * пара «номер + производитель» уникальна.
 */
public record Part(UUID id, Long version, String name, String catalogNumber, String manufacturer, String defaultUnit,
                   boolean fluid, String notes) {

    public Part {
        Objects.requireNonNull(id, "id");
        name = Strings.trimToNull(name);
        catalogNumber = Strings.normalizeCode(catalogNumber);
        manufacturer = Strings.trimToNull(manufacturer);
        defaultUnit = Strings.normalizeCode(defaultUnit);
        notes = Strings.trimToNull(notes);
        if (name == null) {
            throw new BusinessRuleException("name", "Укажите наименование");
        }
        if (defaultUnit == null) {
            throw new BusinessRuleException("defaultUnit", "Укажите единицу измерения");
        }
    }
}
