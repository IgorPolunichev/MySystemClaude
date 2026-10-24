package com.company.acts.directory.adapter.out.persistence;

import com.company.acts.common.persistence.AuditedJpaEntity;
import com.company.acts.directory.domain.MeterType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.util.UUID;

@Entity
@Table(name = "equipment")
public class EquipmentJpaEntity extends AuditedJpaEntity {

    @Column(name = "organization_id")
    private UUID organizationId;

    @Column(name = "department_id")
    private UUID departmentId;

    @Column(name = "site_id")
    private UUID siteId;

    @Column(name = "equipment_type_id")
    private UUID equipmentTypeId;

    @Column(name = "brand")
    private String brand;

    @Column(name = "model")
    private String model;

    @Column(name = "serial_number")
    private String serialNumber;

    @Column(name = "garage_number")
    private String garageNumber;

    @Column(name = "plate_number")
    private String plateNumber;

    @Enumerated(EnumType.STRING)
    @Column(name = "meter_type")
    private MeterType meterType;

    @Column(name = "current_hours")
    private BigDecimal currentHours;

    @Column(name = "current_km")
    private BigDecimal currentKm;

    @Column(name = "manufacture_year")
    private Integer year;

    @Column(name = "notes")
    private String notes;

    public UUID getOrganizationId() {
        return organizationId;
    }

    public void setOrganizationId(UUID organizationId) {
        this.organizationId = organizationId;
    }

    public UUID getDepartmentId() {
        return departmentId;
    }

    public void setDepartmentId(UUID departmentId) {
        this.departmentId = departmentId;
    }

    public UUID getEquipmentTypeId() {
        return equipmentTypeId;
    }

    public void setEquipmentTypeId(UUID equipmentTypeId) {
        this.equipmentTypeId = equipmentTypeId;
    }

    public String getBrand() {
        return brand;
    }

    public void setBrand(String brand) {
        this.brand = brand;
    }

    public String getModel() {
        return model;
    }

    public void setModel(String model) {
        this.model = model;
    }

    public String getSerialNumber() {
        return serialNumber;
    }

    public void setSerialNumber(String serialNumber) {
        this.serialNumber = serialNumber;
    }

    public String getGarageNumber() {
        return garageNumber;
    }

    public void setGarageNumber(String garageNumber) {
        this.garageNumber = garageNumber;
    }

    public String getPlateNumber() {
        return plateNumber;
    }

    public void setPlateNumber(String plateNumber) {
        this.plateNumber = plateNumber;
    }

    public MeterType getMeterType() {
        return meterType;
    }

    public void setMeterType(MeterType meterType) {
        this.meterType = meterType;
    }

    public BigDecimal getCurrentHours() {
        return currentHours;
    }

    public void setCurrentHours(BigDecimal currentHours) {
        this.currentHours = currentHours;
    }

    public BigDecimal getCurrentKm() {
        return currentKm;
    }

    public void setCurrentKm(BigDecimal currentKm) {
        this.currentKm = currentKm;
    }

    public Integer getYear() {
        return year;
    }

    public void setYear(Integer year) {
        this.year = year;
    }

    public String getNotes() {
        return notes;
    }

    public void setNotes(String notes) {
        this.notes = notes;
    }

    public UUID getSiteId() {
        return siteId;
    }

    public void setSiteId(UUID siteId) {
        this.siteId = siteId;
    }
}
