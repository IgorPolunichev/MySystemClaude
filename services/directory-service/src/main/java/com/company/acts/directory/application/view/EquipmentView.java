package com.company.acts.directory.application.view;

import com.company.acts.directory.domain.MeterType;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/** Техника для API: с названиями организации, подразделения и типа. */
public record EquipmentView(
        UUID id,
        UUID organizationId,
        String organizationName,
        UUID departmentId,
        String departmentName,
        UUID siteId,
        String siteName,
        UUID equipmentTypeId,
        String equipmentTypeName,
        String brand,
        String model,
        String serialNumber,
        String garageNumber,
        String plateNumber,
        MeterType meterType,
        BigDecimal currentHours,
        BigDecimal currentKm,
        Integer year,
        String notes,
        Long version,
        Instant createdAt,
        Instant updatedAt) {
}
