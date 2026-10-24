package com.company.acts.directory.adapter.out.persistence;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SiteJpaRepository extends JpaRepository<SiteJpaEntity, UUID> {

    List<SiteJpaEntity> findByOrganizationIdAndDeletedAtIsNullOrderByNameAsc(UUID organizationId);
}
