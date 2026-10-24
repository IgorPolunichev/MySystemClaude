package com.company.acts.directory.application.command;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.UUID;

public record ExecutorCommand(
        UUID id,
        Long version,
        @NotBlank(message = "Укажите наименование") @Size(max = 300) String name,
        @Size(max = 150) String shortName,
        @Size(max = 12) String inn,
        @Size(max = 9) String kpp,
        @Size(max = 15) String ogrn,
        @Size(max = 500) String address,
        @Size(max = 50) String phone,
        @Email(message = "Неверный адрес почты") @Size(max = 150) String email,
        @Size(max = 1000) String bankDetails,
        Boolean isDefault,
        Boolean active) {
}
