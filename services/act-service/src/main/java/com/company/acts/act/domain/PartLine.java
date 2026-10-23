package com.company.acts.act.domain;

import com.company.acts.common.core.BusinessRuleException;
import com.company.acts.common.core.Strings;
import java.math.BigDecimal;
import java.util.Objects;
import java.util.UUID;

/** Позиция запчасти или жидкости в акте: из каталога или введённая вручную. */
public record PartLine(UUID id, UUID actId, int position, PartSource source, UUID catalogItemId, String name,
                       String catalogNumber, BigDecimal quantity, UnitOfMeasure unit, String notes) {

    public PartLine {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(actId, "actId");
        Objects.requireNonNull(source, "source");
        Objects.requireNonNull(unit, "unit");
        name = Strings.trimToNull(name);
        catalogNumber = Strings.normalizeCode(catalogNumber);
        notes = Strings.trimToNull(notes);
        if (name == null) {
            throw new BusinessRuleException("name", "Укажите наименование");
        }
        if (source == PartSource.CATALOG && catalogItemId == null) {
            throw new BusinessRuleException("catalogItemId", "Не указана позиция каталога");
        }
        if (source == PartSource.WAREHOUSE) {
            throw new BusinessRuleException("source", "Списание со склада появится вместе с модулем склада");
        }
        unit.validateQuantity(quantity);
    }
}
