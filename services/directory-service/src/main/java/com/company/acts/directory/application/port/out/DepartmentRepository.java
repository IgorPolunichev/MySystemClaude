package com.company.acts.directory.application.port.out;

import com.company.acts.directory.domain.Department;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface DepartmentRepository {

    Optional<Department> findById(UUID id);

    List<Department> findByOrganization(UUID organizationId);

    Department save(Department department);

    void delete(UUID id);
}
