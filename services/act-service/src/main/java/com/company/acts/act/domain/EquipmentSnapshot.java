package com.company.acts.act.domain;

import java.util.UUID;

/** Данные техники на момент работ. */
public record EquipmentSnapshot(UUID id, String typeName, String brand, String model, String serialNumber,
                                String garageNumber, String plateNumber, MeterType meterType) {

    public String title() {
        return brand + " " + model;
    }
}
