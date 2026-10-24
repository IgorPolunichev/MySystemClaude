package com.company.acts.mail.domain;

import com.company.acts.common.core.BusinessRuleException;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

/** Проверка и нормализация списков адресов «Кому», «Копия», «Скрытая копия». */
public final class EmailAddresses {

    private static final Pattern EMAIL = Pattern.compile("^[^\\s@<>,;]+@[^\\s@<>,;]+\\.[^\\s@<>,;]{2,}$");
    public static final int MAX_RECIPIENTS = 50;

    private EmailAddresses() {
    }

    public static boolean isValid(String email) {
        return email != null && email.length() <= 254 && EMAIL.matcher(email.strip()).matches();
    }

    /** Убирает пробелы и повторы (без учёта регистра); ошибка с указанием неверного адреса. */
    public static List<String> normalize(List<String> list, String field) {
        if (list == null) {
            return List.of();
        }
        Set<String> seen = new LinkedHashSet<>();
        List<String> result = new ArrayList<>();
        for (String raw : list) {
            if (raw == null || raw.isBlank()) {
                continue;
            }
            String email = raw.strip();
            if (!isValid(email)) {
                throw new BusinessRuleException(field, "Неверный адрес: " + email);
            }
            if (seen.add(email.toLowerCase(Locale.ROOT))) {
                result.add(email);
            }
        }
        if (result.size() > MAX_RECIPIENTS) {
            throw new BusinessRuleException(field, "Не больше " + MAX_RECIPIENTS + " адресов в одном поле");
        }
        return result;
    }
}
