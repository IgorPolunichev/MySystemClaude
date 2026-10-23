package com.company.acts.act.domain;

/** Жизненный цикл акта (раздел 6.8 SPEC.md): черновик → открыт (номер) → закрыт; аннулирование — из черновика или открытого. */
public enum ActStatus {
    DRAFT,
    OPEN,
    CLOSED,
    CANCELLED;

    public boolean isEditable() {
        return this == DRAFT || this == OPEN;
    }
}
