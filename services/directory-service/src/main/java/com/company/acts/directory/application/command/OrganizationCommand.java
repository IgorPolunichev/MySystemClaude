package com.company.acts.directory.application.command;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.UUID;

/** Создание или изменение организации (тело запроса OrganizationInput). */
public record OrganizationCommand(
        UUID id,
        Long version,
        @NotBlank(message = "Укажите наименование") @Size(max = 300) String name,
        @Size(max = 150) String shortName,
        @Size(max = 12) String inn,
        @Size(max = 9) String kpp,
        @Size(max = 15) String ogrn,
        @Size(max = 500) String legalAddress,
        @Size(max = 500) String actualAddress,
        @Size(max = 1000) String bankDetails,
        @Size(max = 50) String phone,
        @Email(message = "Неверный адрес почты") @Size(max = 150) String email,
        @Size(max = 2000) String notes,
        Boolean active) {
}
