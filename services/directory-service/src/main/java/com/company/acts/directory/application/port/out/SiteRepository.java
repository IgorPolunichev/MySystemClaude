package com.company.acts.directory.application.port.out;

import com.company.acts.directory.domain.Site;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface SiteRepository {

    Optional<Site> findById(UUID id);

    List<Site> findByOrganization(UUID organizationId, boolean includeInactive);

    Site save(Site site);
}
