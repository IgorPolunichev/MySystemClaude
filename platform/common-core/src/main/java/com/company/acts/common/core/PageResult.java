package com.company.acts.common.core;

import java.util.List;
import java.util.function.Function;

/** Страница результатов прикладного слоя. */
public record PageResult<T>(List<T> items, int page, int size, long totalElements) {

    public <R> PageResult<R> map(Function<T, R> mapper) {
        return new PageResult<>(items.stream().map(mapper).toList(), page, size, totalElements);
    }
}
