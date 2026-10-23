package com.company.acts.common.persistence;

import jakarta.persistence.EntityManager;
import jakarta.persistence.TypedQuery;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Сборщик условий WHERE для реестров: добавляются только заданные фильтры, параметры
 * привязываются по имени. Один и тот же набор условий используется для выборки и подсчёта.
 */
public final class JpqlFilter {

    private final List<String> conditions = new ArrayList<>();
    private final Map<String, Object> params = new LinkedHashMap<>();

    public JpqlFilter where(String condition) {
        conditions.add(condition);
        return this;
    }

    public JpqlFilter where(String condition, String param, Object value) {
        if (value != null) {
            conditions.add(condition);
            params.put(param, value);
        }
        return this;
    }

    /** Поиск подстроки без учёта регистра по нескольким выражениям: {@code (lower(a) like :q or ...)}. */
    public JpqlFilter search(String text, String... expressions) {
        if (text == null || text.isBlank()) {
            return this;
        }
        String pattern = "%" + text.strip().toLowerCase(java.util.Locale.ROOT)
                .replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_") + "%";
        List<String> parts = new ArrayList<>();
        for (String expression : expressions) {
            parts.add("lower(" + expression + ") like :q escape '\\'");
        }
        conditions.add("(" + String.join(" or ", parts) + ")");
        params.put("q", pattern);
        return this;
    }

    public String clause() {
        return conditions.isEmpty() ? "" : " where " + String.join(" and ", conditions);
    }

    public <T> TypedQuery<T> query(EntityManager em, String jpql, Class<T> type) {
        TypedQuery<T> query = em.createQuery(jpql, type);
        params.forEach(query::setParameter);
        return query;
    }
}
