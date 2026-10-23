package com.company.acts.directory.application.view;

import java.time.Instant;
import java.util.UUID;

/** Инженер для API: с ФИО в коротком виде и названием отдела. */
public record EngineerView(
        UUID id,
        String keycloakUserId,
        String username,
        String lastName,
        String firstName,
        String middleName,
        String fullName,
        String position,
        UUID serviceDepartmentId,
        String serviceDepartmentName,
        String phone,
        String email,
        boolean active,
        Long version,
        Instant createdAt,
        Instant updatedAt) {
}
