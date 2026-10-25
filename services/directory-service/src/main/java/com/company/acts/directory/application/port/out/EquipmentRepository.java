package com.company.acts.directory.application.port.out;

import com.company.acts.common.core.PageQuery;
import com.company.acts.common.core.PageResult;
import com.company.acts.directory.application.view.EquipmentView;
import com.company.acts.directory.domain.Equipment;
import java.util.Collection;
import java.util.Optional;
import java.util.UUID;

public interface EquipmentRepository {

    Optional<Equipment> findById(UUID id);

    Optional<EquipmentView> findViewById(UUID id);

    /** Поиск по уже нормализованному номеру в пределах организации. */
    Optional<Equipment> findBySerial(UUID organizationId, String serialNumber);

    Optional<Equipment> findByGarageNumber(UUID organizationId, String garageNumber);

    /** VIN / серийный номер уникален во всей системе (раздел 19.16 SPEC.md). */
    Optional<Equipment> findBySerialAnywhere(String serialNumber);

    /** Гаражный номер уникален на площадке; у техники без площадки — в организации. */
    Optional<Equipment> findByGarageAtSite(UUID organizationId, UUID siteId, String garageNumber);

    Optional<Equipment> findByPlateNumber(UUID organizationId, String plateNumber);

    PageResult<EquipmentView> search(String text, UUID organizationId, Collection<UUID> departmentIds,
                                     UUID equipmentTypeId, PageQuery page);

    boolean existsByOrganization(UUID organizationId);

    boolean existsByDepartment(UUID departmentId);

    boolean existsByType(UUID equipmentTypeId);

    Equipment save(Equipment equipment);

    void delete(UUID id);
}
