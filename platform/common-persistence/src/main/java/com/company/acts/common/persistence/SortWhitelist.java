package com.company.acts.common.persistence;

import com.company.acts.common.core.BusinessRuleException;
import java.util.Map;

/**
 * Белый список полей сортировки: имя из API → выражение JPQL. Защищает от подстановки
 * произвольного текста в ORDER BY.
 */
public record SortWhitelist(Map<String, String> fields, String defaultExpression) {

    public String orderBy(String field, boolean ascending) {
        String expression = field == null ? defaultExpression : fields.get(field);
        if (expression == null) {
            throw new BusinessRuleException("sort", "Сортировка по полю «" + field + "» не поддерживается");
        }
        return " order by " + expression + (ascending ? " asc" : " desc") + ", e.id asc";
    }
}
