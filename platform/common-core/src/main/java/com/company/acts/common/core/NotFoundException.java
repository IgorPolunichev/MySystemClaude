package com.company.acts.common.core;

/** Запрошенная запись не найдена (HTTP 404). */
public class NotFoundException extends RuntimeException {

    public NotFoundException(String message) {
        super(message);
    }

    public static NotFoundException of(String entityName, Object id) {
        return new NotFoundException(entityName + " не найден(а): " + id);
    }
}
