package com.company.acts.common.web.paging;

import java.util.List;

/**
 * Единый формат постраничного ответа для всех реестров.
 *
 * @param items         элементы текущей страницы
 * @param page          номер страницы, с 0
 * @param size          размер страницы
 * @param totalElements общее число записей с учётом фильтров
 */
public record PageResponse<T>(List<T> items, int page, int size, long totalElements) {

    public int totalPages() {
        return size == 0 ? 0 : (int) Math.ceil((double) totalElements / size);
    }
}
