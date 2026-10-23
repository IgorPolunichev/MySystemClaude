package com.company.acts.common.importing;

import com.company.acts.common.core.Strings;
import java.math.BigDecimal;
import java.util.Map;

/** Строка файла импорта: значения по ключам колонок и номер строки в файле (для сообщений об ошибках). */
public record ImportRow(int rowNumber, Map<String, String> values) {

    public String text(String key) {
        return Strings.trimToNull(values.get(key));
    }

    public BigDecimal decimal(String key) {
        String value = text(key);
        if (value == null) {
            return null;
        }
        try {
            return new BigDecimal(value.replace("\u00A0", "").replace(" ", "").replace(',', '.'));
        } catch (NumberFormatException e) {
            throw new ImportRowException("Колонка «" + key + "»: «" + value + "» — не число");
        }
    }

    public Integer integer(String key) {
        BigDecimal value = decimal(key);
        if (value == null) {
            return null;
        }
        try {
            return value.intValueExact();
        } catch (ArithmeticException e) {
            throw new ImportRowException("Колонка «" + key + "»: ожидается целое число");
        }
    }

    public boolean isEmpty() {
        return values.values().stream().allMatch(v -> v == null || v.isBlank());
    }
}
