package com.company.acts.act.application.port.out;

import java.math.BigDecimal;
import java.util.UUID;

/** Справочники (directory-service): данные для снимков в акте и учётные записи инженеров. */
public interface DirectoryClient {

    record EquipmentInfo(UUID id, UUID organizationId, UUID departmentId, String departmentName, UUID siteId,
                         String equipmentTypeName, String brand, String model, String serialNumber,
                         String garageNumber, String plateNumber, String meterType, BigDecimal currentHours,
                         BigDecimal currentKm) {
    }

    record OrganizationInfo(UUID id, String name, String shortName, String inn, String kpp, String legalAddress) {
    }

    record EngineerInfo(UUID id, String keycloakUserId, String fullName, String position, boolean active) {
    }

    /** Исполнитель — одна из компаний, от имени которых оказываются услуги. */
    record ExecutorInfo(UUID id, String name, String shortName, String inn, String kpp, String address, String phone,
                        boolean active) {
    }

    /** Производственная площадка заказчика. */
    record SiteInfo(UUID id, UUID organizationId, String name, String code, String address, boolean active) {
    }

    ExecutorInfo executor(UUID id);

    /** Основной исполнитель; null, если не назначен. */
    ExecutorInfo defaultExecutor();

    SiteInfo site(UUID id);

    EquipmentInfo equipment(UUID id);

    OrganizationInfo organization(UUID id);

    EngineerInfo engineer(UUID id);

    /** Карточка инженера текущего пользователя. */
    EngineerInfo currentEngineer();

    /** Поднять текущую наработку техники, если новые значения больше прежних. Ошибки не прерывают сценарий. */
    void raiseMeter(UUID equipmentId, BigDecimal hours, BigDecimal km);
}
