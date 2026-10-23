package com.company.acts.parts.application.command;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.UUID;

public record PartCommand(
        UUID id,
        Long version,
        @NotBlank(message = "Укажите наименование") @Size(max = 300) String name,
        @Size(max = 100) String catalogNumber,
        @Size(max = 100) String manufacturer,
        @NotBlank(message = "Укажите единицу измерения") @Size(max = 10) String defaultUnit,
        Boolean fluid,
        @Size(max = 2000) String notes) {
}
