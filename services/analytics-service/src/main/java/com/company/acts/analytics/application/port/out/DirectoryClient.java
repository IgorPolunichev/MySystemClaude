package com.company.acts.analytics.application.port.out;

import java.util.List;
import java.util.UUID;

/** Инженеры и отделы сервисной компании из directory-service. */
public interface DirectoryClient {

    record EngineerRef(UUID id, String keycloakUserId, String fullName, UUID departmentId, String departmentName,
                       boolean active) {
    }

    record DepartmentRef(UUID id, String name) {
    }

    List<EngineerRef> engineers();

    List<DepartmentRef> departments();

    /** Карточка инженера текущего пользователя; null, если её нет. */
    EngineerRef me();
}
