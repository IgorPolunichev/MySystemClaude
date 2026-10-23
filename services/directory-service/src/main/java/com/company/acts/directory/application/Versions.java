package com.company.acts.directory.application;

import com.company.acts.common.core.BusinessRuleException;
import com.company.acts.common.core.ConflictException;
import java.util.Objects;

/** Проверка версии записи при изменении (оптимистичная блокировка на уровне сценария). */
final class Versions {

    private Versions() {
    }

    static void check(Long expected, Long actual) {
        if (expected == null) {
            throw new BusinessRuleException("version", "Не передана версия записи");
        }
        if (!Objects.equals(expected, actual)) {
            throw ConflictException.staleVersion();
        }
    }
}
