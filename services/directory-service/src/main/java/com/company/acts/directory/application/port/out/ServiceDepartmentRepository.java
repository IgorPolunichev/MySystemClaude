package com.company.acts.directory.application.port.out;

import com.company.acts.directory.domain.ServiceDepartment;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ServiceDepartmentRepository {

    Optional<ServiceDepartment> findById(UUID id);

    Optional<ServiceDepartment> findByNameIgnoreCase(String name);

    List<ServiceDepartment> findAll();

    ServiceDepartment save(ServiceDepartment department);

    void delete(UUID id);
}
