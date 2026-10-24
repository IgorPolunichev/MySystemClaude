package com.company.acts.directory.application.command;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.UUID;

public record SiteCommand(
        UUID id,
        Long version,
        @NotNull(message = "Укажите организацию") UUID organizationId,
        @NotBlank(message = "Укажите название площадки") @Size(max = 200) String name,
        @Size(max = 6) String code,
        @Size(max = 500) String address,
        Boolean active) {
}
