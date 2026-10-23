package com.company.acts.directory.application;

/** Выражения проверки прав для @PreAuthorize (раздел 11 SPEC.md). */
final class Roles {

    /** Изменять и удалять записи справочников. */
    static final String CAN_EDIT = "hasAnyRole('DEPARTMENT_HEAD', 'ADMIN')";
    /** Управлять инженерами и отделами сервисной компании. */
    static final String ADMIN = "hasRole('ADMIN')";

    private Roles() {
    }
}
