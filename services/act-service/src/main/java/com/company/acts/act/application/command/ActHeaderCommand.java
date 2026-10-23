package com.company.acts.act.application.command;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/** Шапка и описания акта. При изменении закрытого акта администратор обязан указать adminComment. */
public record ActHeaderCommand(
        @NotNull(message = "Не передана версия записи") Long version,
        UUID equipmentId,
        @DecimalMin(value = "0", message = "Не может быть отрицательным") BigDecimal meterHours,
        @DecimalMin(value = "0", message = "Не может быть отрицательным") BigDecimal meterKm,
        @Size(max = 300) String location,
        LocalDate workStartDate,
        LocalDate workEndDate,
        @Size(max = 100, message = "Не длиннее 100 символов") String shortDescription,
        @Size(max = 10000) String detailedDescription,
        @Size(max = 10000) String recommendations,
        @Size(max = 200) String customerRepName,
        @Size(max = 200) String customerRepPosition,
        Boolean hideRequisites,
        Boolean hideTravelTime,
        @Size(max = 1000) String adminComment) {
}
