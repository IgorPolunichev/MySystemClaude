package com.company.acts.common.core;

/** Нарушено бизнес-правило или данные не прошли доменную проверку (HTTP 422). */
public class BusinessRuleException extends RuntimeException {

    private final String field;

    public BusinessRuleException(String message) {
        this(null, message);
    }

    public BusinessRuleException(String field, String message) {
        super(message);
        this.field = field;
    }

    /** Поле формы, к которому относится ошибка, или {@code null}. */
    public String field() {
        return field;
    }
}
