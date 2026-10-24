package com.company.acts.directory.adapter.out.persistence;

import com.company.acts.common.persistence.AuditedJpaEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

@Entity
@Table(name = "executor_company")
public class ExecutorJpaEntity extends AuditedJpaEntity {

    @Column(name = "name")
    String name;
    @Column(name = "short_name")
    String shortName;
    @Column(name = "inn")
    String inn;
    @Column(name = "kpp")
    String kpp;
    @Column(name = "ogrn")
    String ogrn;
    @Column(name = "address")
    String address;
    @Column(name = "phone")
    String phone;
    @Column(name = "email")
    String email;
    @Column(name = "bank_details")
    String bankDetails;
    @Column(name = "is_default")
    boolean isDefault;
    @Column(name = "is_active")
    boolean active;
}
