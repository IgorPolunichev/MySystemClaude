package com.company.acts.directory.application.command;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.UUID;

/** Справочник из одного названия: тип техники, отдел сервисной компании. */
public record NamedCommand(
        UUID id,
        Long version,
        @NotBlank(message = "Укажите название") @Size(max = 200) String name) {
}
