package com.company.acts.common.core;

/** Нормализация пользовательского ввода. */
public final class Strings {

    private Strings() {
    }

    /** Обрезает пробелы; пустую строку превращает в {@code null}. */
    public static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.strip();
        return trimmed.isEmpty() ? null : trimmed;
    }

    /** Номер техники или запчасти: без пробелов по краям, в верхнем регистре; пустой — {@code null}. */
    public static String normalizeCode(String value) {
        String trimmed = trimToNull(value);
        return trimmed == null ? null : trimmed.toUpperCase(java.util.Locale.ROOT);
    }
}
