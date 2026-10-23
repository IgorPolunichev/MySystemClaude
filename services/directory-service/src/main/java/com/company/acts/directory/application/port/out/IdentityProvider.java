package com.company.acts.directory.application.port.out;

import com.company.acts.directory.domain.EngineerRole;

/** Учётные записи пользователей во внешней системе входа (Keycloak). */
public interface IdentityProvider {

    /** Создаёт пользователя с временным паролем и ролью; возвращает его id во внешней системе. */
    String createUser(String username, String temporaryPassword, String firstName, String lastName, String email,
                      EngineerRole role);

    void updateUser(String userId, String firstName, String lastName, String email, boolean enabled);

    /** Удаление — только для отката, если сохранить инженера в БД не удалось. */
    void deleteUser(String userId);
}
