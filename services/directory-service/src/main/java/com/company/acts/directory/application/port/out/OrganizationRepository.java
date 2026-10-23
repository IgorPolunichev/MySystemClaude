package com.company.acts.directory.application.port.out;

import com.company.acts.common.core.PageQuery;
import com.company.acts.common.core.PageResult;
import com.company.acts.directory.domain.Organization;
import java.util.Optional;
import java.util.UUID;

public interface OrganizationRepository {

    Optional<Organization> findById(UUID id);

    Optional<Organization> findByInn(String inn);

    Optional<Organization> findByNameIgnoreCase(String name);

    PageResult<Organization> search(String text, Boolean active, PageQuery page);

    Organization save(Organization organization);

    void delete(UUID id);
}
