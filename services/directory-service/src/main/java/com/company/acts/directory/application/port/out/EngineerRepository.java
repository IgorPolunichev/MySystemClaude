package com.company.acts.directory.application.port.out;

import com.company.acts.common.core.PageQuery;
import com.company.acts.common.core.PageResult;
import com.company.acts.directory.application.view.EngineerView;
import com.company.acts.directory.domain.Engineer;
import java.util.Optional;
import java.util.UUID;

public interface EngineerRepository {

    Optional<Engineer> findById(UUID id);

    Optional<EngineerView> findViewById(UUID id);

    Optional<Engineer> findByUsername(String username);

    Optional<Engineer> findByKeycloakUserId(String keycloakUserId);

    PageResult<EngineerView> search(String text, UUID serviceDepartmentId, Boolean active, PageQuery page);

    boolean existsByServiceDepartment(UUID serviceDepartmentId);

    Engineer save(Engineer engineer);
}
