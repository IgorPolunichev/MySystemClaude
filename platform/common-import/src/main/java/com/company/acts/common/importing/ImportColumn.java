package com.company.acts.common.importing;

import java.util.List;
import java.util.Locale;

/**
 * Колонка файла импорта: ключ для кода и допустимые заголовки (первый — тот, что пишется в шаблон).
 * Заголовки сравниваются без учёта регистра, пробелов и знаков препинания.
 */
public record ImportColumn(String key, boolean required, List<String> headers, String hint) {

    public static ImportColumn required(String key, String hint, String... headers) {
        return new ImportColumn(key, true, List.of(headers), hint);
    }

    public static ImportColumn optional(String key, String hint, String... headers) {
        return new ImportColumn(key, false, List.of(headers), hint);
    }

    public String title() {
        return headers.getFirst();
    }

    boolean matches(String header) {
        String normalized = normalize(header);
        return headers.stream().anyMatch(h -> normalize(h).equals(normalized));
    }

    static String normalize(String header) {
        return header == null ? "" : header.toLowerCase(Locale.ROOT).replace('ё', 'е').replaceAll("[^\\p{L}\\p{Nd}]", "");
    }
}
