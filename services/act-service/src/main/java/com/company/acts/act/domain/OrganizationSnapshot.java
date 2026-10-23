package com.company.acts.act.domain;

import java.util.UUID;

/** Данные организации на момент работ: правка справочника не меняет уже выданный акт. */
public record OrganizationSnapshot(UUID id, String name, String shortName, String inn, String kpp,
                                   String legalAddress, UUID departmentId, String departmentName) {

    public String displayName() {
        return shortName != null ? shortName : name;
    }
}
