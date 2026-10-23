package com.company.acts.parts.adapter.out.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** Единица измерения — справочник, заполняемый миграцией. */
@Entity
@Table(name = "unit_of_measure")
public class UnitJpaEntity {

    @Id
    @Column(name = "code")
    private String code;

    @Column(name = "name_ru")
    private String name;

    @Column(name = "allows_fraction")
    private boolean allowsFraction;

    @Column(name = "sort_order")
    private int sortOrder;

    public String getCode() {
        return code;
    }

    public String getName() {
        return name;
    }

    public boolean isAllowsFraction() {
        return allowsFraction;
    }

    public int getSortOrder() {
        return sortOrder;
    }
}
