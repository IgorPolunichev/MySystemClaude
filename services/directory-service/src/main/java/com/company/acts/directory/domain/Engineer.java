package com.company.acts.directory.domain;

import com.company.acts.common.core.BusinessRuleException;
import com.company.acts.common.core.Strings;
import java.util.Objects;
import java.util.UUID;

/** Сервисный инженер — сотрудник сервисной компании с учётной записью в Keycloak. */
public record Engineer(
        UUID id,
        Long version,
        String keycloakUserId,
        String username,
        String lastName,
        String firstName,
        String middleName,
        String position,
        UUID serviceDepartmentId,
        String phone,
        String email,
        boolean active) {

    public Engineer {
        Objects.requireNonNull(id, "id");
        username = Strings.trimToNull(username);
        lastName = Strings.trimToNull(lastName);
        firstName = Strings.trimToNull(firstName);
        middleName = Strings.trimToNull(middleName);
        position = Strings.trimToNull(position);
        phone = Strings.trimToNull(phone);
        email = Strings.trimToNull(email);
        if (username == null) {
            throw new BusinessRuleException("username", "Укажите логин");
        }
        username = username.toLowerCase(java.util.Locale.ROOT);
        if (lastName == null) {
            throw new BusinessRuleException("lastName", "Укажите фамилию");
        }
        if (firstName == null) {
            throw new BusinessRuleException("firstName", "Укажите имя");
        }
    }

    /** «Петров И. С.» — для таблиц, актов и подписей. */
    public String fullName() {
        StringBuilder sb = new StringBuilder(lastName).append(' ').append(firstName.charAt(0)).append('.');
        if (middleName != null) {
            sb.append(' ').append(middleName.charAt(0)).append('.');
        }
        return sb.toString();
    }

    public Engineer withKeycloakUserId(String keycloakId) {
        return new Engineer(id, version, keycloakId, username, lastName, firstName, middleName, position,
                serviceDepartmentId, phone, email, active);
    }
}
