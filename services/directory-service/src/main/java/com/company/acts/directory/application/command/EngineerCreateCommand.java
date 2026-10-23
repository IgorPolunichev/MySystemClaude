package com.company.acts.directory.application.command;

import com.company.acts.directory.domain.EngineerRole;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.UUID;

public record EngineerCreateCommand(
        UUID id,
        @NotBlank(message = "Укажите логин") @Size(min = 3, max = 50)
        @Pattern(regexp = "^[a-zA-Z0-9._-]+$", message = "Логин: латинские буквы, цифры, точка, дефис, подчёркивание")
        String username,
        @NotBlank(message = "Укажите временный пароль") @Size(min = 8, max = 100, message = "Пароль — не короче 8 символов")
        String initialPassword,
        @NotNull(message = "Укажите роль") EngineerRole role,
        @NotBlank(message = "Укажите фамилию") @Size(max = 100) String lastName,
        @NotBlank(message = "Укажите имя") @Size(max = 100) String firstName,
        @Size(max = 100) String middleName,
        @Size(max = 150) String position,
        UUID serviceDepartmentId,
        @Size(max = 50) String phone,
        @Email(message = "Неверный адрес почты") @Size(max = 150) String email) {
}
