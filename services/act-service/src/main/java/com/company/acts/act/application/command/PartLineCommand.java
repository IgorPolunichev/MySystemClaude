package com.company.acts.act.application.command;

import com.company.acts.act.domain.PartSource;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.util.UUID;

/**
 * Позиция запчасти. Для {@code MANUAL} флаг {@code addToCatalog} добавляет позицию в каталог, и она
 * сохраняется уже как {@code CATALOG}.
 */
public record PartLineCommand(
        UUID id,
        @NotNull(message = "Укажите источник") PartSource source,
        UUID catalogItemId,
        @NotBlank(message = "Укажите наименование") @Size(max = 300) String name,
        @Size(max = 100) String catalogNumber,
        @NotNull(message = "Укажите количество") BigDecimal quantity,
        @NotBlank(message = "Укажите единицу измерения") String unitCode,
        @Size(max = 500) String notes,
        Boolean addToCatalog) {
}
