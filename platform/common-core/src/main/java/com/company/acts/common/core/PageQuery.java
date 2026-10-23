package com.company.acts.common.core;

/**
 * Параметры страницы и сортировки, не зависящие от Spring Data.
 *
 * @param page      номер страницы с 0
 * @param size      размер страницы, 1..200
 * @param sortField поле сортировки (имя из белого списка ресурса) или {@code null}
 * @param ascending направление сортировки
 */
public record PageQuery(int page, int size, String sortField, boolean ascending) {

    public static final int DEFAULT_SIZE = 50;
    public static final int MAX_SIZE = 200;

    public PageQuery {
        page = Math.max(page, 0);
        size = size <= 0 ? DEFAULT_SIZE : Math.min(size, MAX_SIZE);
    }

    /** Разбирает параметр вида {@code name} (по возрастанию) или {@code -name} (по убыванию). */
    public static PageQuery of(Integer page, Integer size, String sort) {
        String field = null;
        boolean asc = true;
        if (sort != null && !sort.isBlank()) {
            String s = sort.trim();
            if (s.startsWith("-")) {
                asc = false;
                s = s.substring(1);
            }
            field = s.isBlank() ? null : s;
        }
        return new PageQuery(page == null ? 0 : page, size == null ? DEFAULT_SIZE : size, field, asc);
    }

    public int offset() {
        return page * size;
    }
}
