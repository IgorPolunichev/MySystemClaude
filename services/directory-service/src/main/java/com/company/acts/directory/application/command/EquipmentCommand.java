package com.company.acts.directory.application.command;

import com.company.acts.directory.domain.MeterType;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.util.UUID;

public record EquipmentCommand(
        UUID id,
        Long version,
        @NotNull(message = "Укажите организацию") UUID organizationId,
        UUID departmentId,
        UUID siteId,
        @NotNull(message = "Укажите тип техники") UUID equipmentTypeId,
        @NotBlank(message = "Укажите марку") @Size(max = 100) String brand,
        @NotBlank(message = "Укажите модель") @Size(max = 100) String model,
        @Size(max = 100) String serialNumber,
        @Size(max = 50) String garageNumber,
        @Size(max = 20) String plateNumber,
        @NotNull(message = "Укажите учёт наработки") MeterType meterType,
        @DecimalMin(value = "0", message = "Не может быть отрицательным") BigDecimal currentHours,
        @DecimalMin(value = "0", message = "Не может быть отрицательным") BigDecimal currentKm,
        Integer year,
        @Size(max = 2000) String notes) {
}
