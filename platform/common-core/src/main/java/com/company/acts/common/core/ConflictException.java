package com.company.acts.common.core;

/**
 * Конфликт с текущим состоянием данных (HTTP 409): запись изменена другим пользователем,
 * дубликат уникального значения, удаление используемой записи.
 */
public class ConflictException extends RuntimeException {

    public ConflictException(String message) {
        super(message);
    }

    public static ConflictException staleVersion() {
        return new ConflictException("Запись изменена другим пользователем. Обновите страницу и повторите изменение.");
    }
}
