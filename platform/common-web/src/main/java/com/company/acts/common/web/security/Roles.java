package com.company.acts.common.web.security;

/** Роли системы (realm roles в Keycloak). Права на действия описываются отдельно, через permissions. */
public final class Roles {
    public static final String ENGINEER = "ENGINEER";
    public static final String DEPARTMENT_HEAD = "DEPARTMENT_HEAD";
    public static final String ADMIN = "ADMIN";

    private Roles() {
    }
}
