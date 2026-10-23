package com.company.acts.directory.application.port.out;

import com.company.acts.directory.domain.EquipmentType;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface EquipmentTypeRepository {

    Optional<EquipmentType> findById(UUID id);

    Optional<EquipmentType> findByNameIgnoreCase(String name);

    List<EquipmentType> findAll();

    EquipmentType save(EquipmentType type);

    void delete(UUID id);
}
