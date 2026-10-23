package com.company.acts.directory.domain;

import com.company.acts.common.core.BusinessRuleException;
import com.company.acts.common.core.Strings;
import java.math.BigDecimal;
import java.util.Objects;
import java.util.UUID;

/**
 * Единица техники заказчика. Номера хранятся в верхнем регистре без пробелов по краям, чтобы поиск и
 * проверка уникальности не зависели от того, как их ввели.
 */
public record Equipment(
        UUID id,
        Long version,
        UUID organizationId,
        UUID departmentId,
        UUID equipmentTypeId,
        String brand,
        String model,
        String serialNumber,
        String garageNumber,
        String plateNumber,
        MeterType meterType,
        BigDecimal currentHours,
        BigDecimal currentKm,
        Integer year,
        String notes) {

    public Equipment {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(organizationId, "organizationId");
        Objects.requireNonNull(equipmentTypeId, "equipmentTypeId");
        brand = Strings.trimToNull(brand);
        model = Strings.trimToNull(model);
        serialNumber = Strings.normalizeCode(serialNumber);
        garageNumber = Strings.normalizeCode(garageNumber);
        plateNumber = plateNumber == null ? null : Strings.normalizeCode(plateNumber.replace(" ", ""));
        notes = Strings.trimToNull(notes);
        meterType = meterType == null ? MeterType.HOURS : meterType;
        if (brand == null) {
            throw new BusinessRuleException("brand", "Укажите марку");
        }
        if (model == null) {
            throw new BusinessRuleException("model", "Укажите модель");
        }
        if (currentHours != null && currentHours.signum() < 0) {
            throw new BusinessRuleException("currentHours", "Моточасы не могут быть отрицательными");
        }
        if (currentKm != null && currentKm.signum() < 0) {
            throw new BusinessRuleException("currentKm", "Пробег не может быть отрицательным");
        }
        if (year != null && (year < 1950 || year > 2100)) {
            throw new BusinessRuleException("year", "Год выпуска указан неверно");
        }
    }
}
