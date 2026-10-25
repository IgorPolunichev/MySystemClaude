package com.company.acts.act.domain;

import java.util.Set;

/** Пользователь, выполняющий действие: id и логин в Keycloak, роли. */
public record CurrentUser(String userId, String username, Set<String> roles) {

    public boolean isEngineer() {
        return roles.contains("ENGINEER");
    }

    public boolean isAdmin() {
        return roles.contains("ADMIN");
    }

    public boolean isHead() {
        return roles.contains("DEPARTMENT_HEAD");
    }

    /** Руководитель или администратор — может править любой незакрытый акт. */
    public boolean isSupervisor() {
        return isAdmin() || isHead();
    }
}
