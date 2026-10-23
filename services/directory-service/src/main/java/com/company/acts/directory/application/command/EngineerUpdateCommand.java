package com.company.acts.directory.application.command;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.UUID;

public record EngineerUpdateCommand(
        @NotBlank(message = "Укажите фамилию") @Size(max = 100) String lastName,
        @NotBlank(message = "Укажите имя") @Size(max = 100) String firstName,
        @Size(max = 100) String middleName,
        @Size(max = 150) String position,
        UUID serviceDepartmentId,
        @Size(max = 50) String phone,
        @Email(message = "Неверный адрес почты") @Size(max = 150) String email,
        boolean active,
        @NotNull(message = "Не передана версия записи") Long version) {
}
