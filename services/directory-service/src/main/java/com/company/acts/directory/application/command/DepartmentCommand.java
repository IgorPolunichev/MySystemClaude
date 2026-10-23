package com.company.acts.directory.application.command;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.UUID;

public record DepartmentCommand(
        UUID id,
        Long version,
        @NotNull(message = "Укажите организацию") UUID organizationId,
        UUID parentId,
        @NotBlank(message = "Укажите название") @Size(max = 200) String name,
        @Size(max = 50) String code) {
}
