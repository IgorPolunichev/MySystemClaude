package com.company.acts.act.domain;

/** Номер акта: {@code АВР-2026-000123}. Формат задаётся настройкой, по умолчанию — этот. */
public final class ActNumberFormat {

    public static final String DEFAULT_PREFIX = "АВР";

    private ActNumberFormat() {
    }

    public static String format(String prefix, int year, long value) {
        return "%s-%d-%06d".formatted(prefix, year, value);
    }
}
