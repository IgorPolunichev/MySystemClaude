package com.company.acts.act.adapter.out.persistence;

import com.company.acts.act.domain.ActStatus;
import com.company.acts.act.domain.MeterType;
import com.company.acts.common.persistence.AuditedJpaEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "work_act")
public class WorkActJpaEntity extends AuditedJpaEntity {

    @Column(name = "executor_id")
    private UUID executorId;

    @Column(name = "executor_name")
    private String executorName;

    @Column(name = "executor_short_name")
    private String executorShortName;

    @Column(name = "executor_inn")
    private String executorInn;

    @Column(name = "executor_kpp")
    private String executorKpp;

    @Column(name = "executor_address")
    private String executorAddress;

    @Column(name = "executor_phone")
    private String executorPhone;

    @Column(name = "site_id")
    private UUID siteId;

    @Column(name = "site_name")
    private String siteName;

    @Column(name = "site_code")
    private String siteCode;

    @Column(name = "site_address")
    private String siteAddress;

    @Column(name = "has_signed_scan", insertable = false, updatable = false)
    private boolean hasSignedScan;

    @Column(name = "number")
    private String number;

    @Enumerated(EnumType.STRING)
    @Column(name = "status")
    private ActStatus status;

    @Column(name = "org_id")
    private UUID orgId;

    @Column(name = "org_name")
    private String orgName;

    @Column(name = "org_short_name")
    private String orgShortName;

    @Column(name = "org_inn")
    private String orgInn;

    @Column(name = "org_kpp")
    private String orgKpp;

    @Column(name = "org_legal_address")
    private String orgLegalAddress;

    @Column(name = "department_id")
    private UUID departmentId;

    @Column(name = "department_name")
    private String departmentName;

    @Column(name = "equipment_id")
    private UUID equipmentId;

    @Column(name = "equipment_type_name")
    private String equipmentTypeName;

    @Column(name = "equipment_brand")
    private String equipmentBrand;

    @Column(name = "equipment_model")
    private String equipmentModel;

    @Column(name = "equipment_serial")
    private String equipmentSerial;

    @Column(name = "equipment_garage")
    private String equipmentGarage;

    @Column(name = "equipment_plate")
    private String equipmentPlate;

    @Enumerated(EnumType.STRING)
    @Column(name = "equipment_meter_type")
    private MeterType equipmentMeterType;

    @Column(name = "meter_hours")
    private BigDecimal meterHours;

    @Column(name = "meter_km")
    private BigDecimal meterKm;

    @Column(name = "location")
    private String location;

    @Column(name = "work_start_override")
    private LocalDate workStartOverride;

    @Column(name = "work_end_override")
    private LocalDate workEndOverride;

    @Column(name = "short_description")
    private String shortDescription;

    @Column(name = "detailed_description")
    private String detailedDescription;

    @Column(name = "recommendations")
    private String recommendations;

    @Column(name = "customer_rep_name")
    private String customerRepName;

    @Column(name = "customer_rep_position")
    private String customerRepPosition;

    @Column(name = "hide_requisites")
    private boolean hideRequisites;

    @Column(name = "hide_travel_time")
    private boolean hideTravelTime;

    @Column(name = "author_engineer_id")
    private UUID authorEngineerId;

    @Column(name = "author_user_id")
    private String authorUserId;

    @Column(name = "closed_at")
    private Instant closedAt;

    @Column(name = "closed_by")
    private String closedBy;

    @Column(name = "period_start", insertable = false, updatable = false)
    private LocalDate periodStart;

    @Column(name = "period_end", insertable = false, updatable = false)
    private LocalDate periodEnd;

    @Column(name = "total_hours", insertable = false, updatable = false)
    private BigDecimal totalHours;

    @Column(name = "engineer_names", insertable = false, updatable = false)
    private String engineerNames;

    public String getNumber() {
        return number;
    }

    public void setNumber(String number) {
        this.number = number;
    }

    public ActStatus getStatus() {
        return status;
    }

    public void setStatus(ActStatus status) {
        this.status = status;
    }

    public UUID getOrgId() {
        return orgId;
    }

    public void setOrgId(UUID orgId) {
        this.orgId = orgId;
    }

    public String getOrgName() {
        return orgName;
    }

    public void setOrgName(String orgName) {
        this.orgName = orgName;
    }

    public String getOrgShortName() {
        return orgShortName;
    }

    public void setOrgShortName(String orgShortName) {
        this.orgShortName = orgShortName;
    }

    public String getOrgInn() {
        return orgInn;
    }

    public void setOrgInn(String orgInn) {
        this.orgInn = orgInn;
    }

    public String getOrgKpp() {
        return orgKpp;
    }

    public void setOrgKpp(String orgKpp) {
        this.orgKpp = orgKpp;
    }

    public String getOrgLegalAddress() {
        return orgLegalAddress;
    }

    public void setOrgLegalAddress(String orgLegalAddress) {
        this.orgLegalAddress = orgLegalAddress;
    }

    public UUID getDepartmentId() {
        return departmentId;
    }

    public void setDepartmentId(UUID departmentId) {
        this.departmentId = departmentId;
    }

    public String getDepartmentName() {
        return departmentName;
    }

    public void setDepartmentName(String departmentName) {
        this.departmentName = departmentName;
    }

    public UUID getEquipmentId() {
        return equipmentId;
    }

    public void setEquipmentId(UUID equipmentId) {
        this.equipmentId = equipmentId;
    }

    public String getEquipmentTypeName() {
        return equipmentTypeName;
    }

    public void setEquipmentTypeName(String equipmentTypeName) {
        this.equipmentTypeName = equipmentTypeName;
    }

    public String getEquipmentBrand() {
        return equipmentBrand;
    }

    public void setEquipmentBrand(String equipmentBrand) {
        this.equipmentBrand = equipmentBrand;
    }

    public String getEquipmentModel() {
        return equipmentModel;
    }

    public void setEquipmentModel(String equipmentModel) {
        this.equipmentModel = equipmentModel;
    }

    public String getEquipmentSerial() {
        return equipmentSerial;
    }

    public void setEquipmentSerial(String equipmentSerial) {
        this.equipmentSerial = equipmentSerial;
    }

    public String getEquipmentGarage() {
        return equipmentGarage;
    }

    public void setEquipmentGarage(String equipmentGarage) {
        this.equipmentGarage = equipmentGarage;
    }

    public String getEquipmentPlate() {
        return equipmentPlate;
    }

    public void setEquipmentPlate(String equipmentPlate) {
        this.equipmentPlate = equipmentPlate;
    }

    public MeterType getEquipmentMeterType() {
        return equipmentMeterType;
    }

    public void setEquipmentMeterType(MeterType equipmentMeterType) {
        this.equipmentMeterType = equipmentMeterType;
    }

    public BigDecimal getMeterHours() {
        return meterHours;
    }

    public void setMeterHours(BigDecimal meterHours) {
        this.meterHours = meterHours;
    }

    public BigDecimal getMeterKm() {
        return meterKm;
    }

    public void setMeterKm(BigDecimal meterKm) {
        this.meterKm = meterKm;
    }

    public String getLocation() {
        return location;
    }

    public void setLocation(String location) {
        this.location = location;
    }

    public LocalDate getWorkStartOverride() {
        return workStartOverride;
    }

    public void setWorkStartOverride(LocalDate workStartOverride) {
        this.workStartOverride = workStartOverride;
    }

    public LocalDate getWorkEndOverride() {
        return workEndOverride;
    }

    public void setWorkEndOverride(LocalDate workEndOverride) {
        this.workEndOverride = workEndOverride;
    }

    public String getShortDescription() {
        return shortDescription;
    }

    public void setShortDescription(String shortDescription) {
        this.shortDescription = shortDescription;
    }

    public String getDetailedDescription() {
        return detailedDescription;
    }

    public void setDetailedDescription(String detailedDescription) {
        this.detailedDescription = detailedDescription;
    }

    public String getRecommendations() {
        return recommendations;
    }

    public void setRecommendations(String recommendations) {
        this.recommendations = recommendations;
    }

    public String getCustomerRepName() {
        return customerRepName;
    }

    public void setCustomerRepName(String customerRepName) {
        this.customerRepName = customerRepName;
    }

    public String getCustomerRepPosition() {
        return customerRepPosition;
    }

    public void setCustomerRepPosition(String customerRepPosition) {
        this.customerRepPosition = customerRepPosition;
    }

    public boolean isHideRequisites() {
        return hideRequisites;
    }

    public void setHideRequisites(boolean hideRequisites) {
        this.hideRequisites = hideRequisites;
    }

    public boolean isHideTravelTime() {
        return hideTravelTime;
    }

    public void setHideTravelTime(boolean hideTravelTime) {
        this.hideTravelTime = hideTravelTime;
    }

    public UUID getAuthorEngineerId() {
        return authorEngineerId;
    }

    public void setAuthorEngineerId(UUID authorEngineerId) {
        this.authorEngineerId = authorEngineerId;
    }

    public String getAuthorUserId() {
        return authorUserId;
    }

    public void setAuthorUserId(String authorUserId) {
        this.authorUserId = authorUserId;
    }

    public Instant getClosedAt() {
        return closedAt;
    }

    public void setClosedAt(Instant closedAt) {
        this.closedAt = closedAt;
    }

    public String getClosedBy() {
        return closedBy;
    }

    public void setClosedBy(String closedBy) {
        this.closedBy = closedBy;
    }

    public LocalDate getPeriodStart() {
        return periodStart;
    }

    public void setPeriodStart(LocalDate periodStart) {
        this.periodStart = periodStart;
    }

    public LocalDate getPeriodEnd() {
        return periodEnd;
    }

    public void setPeriodEnd(LocalDate periodEnd) {
        this.periodEnd = periodEnd;
    }

    public BigDecimal getTotalHours() {
        return totalHours;
    }

    public void setTotalHours(BigDecimal totalHours) {
        this.totalHours = totalHours;
    }

    public String getEngineerNames() {
        return engineerNames;
    }

    public void setEngineerNames(String engineerNames) {
        this.engineerNames = engineerNames;
    }

    public UUID getExecutorId() {
        return executorId;
    }

    public void setExecutorId(UUID executorId) {
        this.executorId = executorId;
    }

    public String getExecutorName() {
        return executorName;
    }

    public void setExecutorName(String executorName) {
        this.executorName = executorName;
    }

    public String getExecutorShortName() {
        return executorShortName;
    }

    public void setExecutorShortName(String executorShortName) {
        this.executorShortName = executorShortName;
    }

    public String getExecutorInn() {
        return executorInn;
    }

    public void setExecutorInn(String executorInn) {
        this.executorInn = executorInn;
    }

    public String getExecutorKpp() {
        return executorKpp;
    }

    public void setExecutorKpp(String executorKpp) {
        this.executorKpp = executorKpp;
    }

    public String getExecutorAddress() {
        return executorAddress;
    }

    public void setExecutorAddress(String executorAddress) {
        this.executorAddress = executorAddress;
    }

    public String getExecutorPhone() {
        return executorPhone;
    }

    public void setExecutorPhone(String executorPhone) {
        this.executorPhone = executorPhone;
    }

    public UUID getSiteId() {
        return siteId;
    }

    public void setSiteId(UUID siteId) {
        this.siteId = siteId;
    }

    public String getSiteName() {
        return siteName;
    }

    public void setSiteName(String siteName) {
        this.siteName = siteName;
    }

    public String getSiteAddress() {
        return siteAddress;
    }

    public void setSiteAddress(String siteAddress) {
        this.siteAddress = siteAddress;
    }

    public boolean isHasSignedScan() {
        return hasSignedScan;
    }

    public String getSiteCode() {
        return siteCode;
    }

    public void setSiteCode(String siteCode) {
        this.siteCode = siteCode;
    }
}
