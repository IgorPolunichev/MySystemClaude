package com.company.acts.directory.adapter.out.persistence;

import com.company.acts.common.persistence.AuditedJpaEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.util.UUID;

@Entity
@Table(name = "site")
public class SiteJpaEntity extends AuditedJpaEntity {

    @Column(name = "organization_id")
    UUID organizationId;
    @Column(name = "name")
    String name;
    @Column(name = "code")
    String code;
    @Column(name = "address")
    String address;
    @Column(name = "is_active")
    boolean active;
}
