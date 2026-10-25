package com.company.acts.analytics.domain.schedule;

/** Что у сотрудника в этот день по графику. */
public enum ShiftKind {
    /** Дневная смена. */
    DAY,
    /** Ночная смена (начинается в этот день). */
    NIGHT,
    /** Отсыпной после ночных смен. */
    REST,
    /** Выходной или межвахта. */
    OFF,
    /** Отпуск (ручная отметка). */
    VACATION,
    /** Больничный (ручная отметка). */
    SICK;

    public boolean working() {
        return this == DAY || this == NIGHT;
    }

    /** Буква в табеле. */
    public String letter() {
        return switch (this) {
            case DAY -> "Д";
            case NIGHT -> "Н";
            case REST -> "О";
            case OFF -> "В";
            case VACATION -> "ОТ";
            case SICK -> "Б";
        };
    }
}
