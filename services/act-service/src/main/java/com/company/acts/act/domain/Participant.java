package com.company.acts.act.domain;

import java.util.Objects;
import java.util.UUID;

/** Инженер в акте; ФИО и должность — снимок для печати, keycloakUserId — для проверки прав. */
public record Participant(UUID engineerId, String keycloakUserId, String fullName, String position,
                          ParticipantRole role) {

    public Participant {
        Objects.requireNonNull(engineerId, "engineerId");
        Objects.requireNonNull(role, "role");
    }

    public boolean isUser(CurrentUser user) {
        return keycloakUserId != null && keycloakUserId.equals(user.userId());
    }
}
