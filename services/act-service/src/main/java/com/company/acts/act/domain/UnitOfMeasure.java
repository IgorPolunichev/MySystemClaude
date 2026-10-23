package com.company.acts.act.domain;

import com.company.acts.common.core.BusinessRuleException;
import java.math.BigDecimal;
import java.util.Locale;

/**
 * Единицы измерения позиций акта. Набор совпадает со справочником parts-service; хранится здесь, чтобы
 * проверять дробность количества без сетевого вызова (ADR 0008).
 */
public enum UnitOfMeasure {
    PCS("шт", false),
    L("л", true),
    KG("кг", true),
    M("м", true),
    SET("компл.", false);

    private final String label;
    private final boolean allowsFraction;

    UnitOfMeasure(String label, boolean allowsFraction) {
        this.label = label;
        this.allowsFraction = allowsFraction;
    }

    public String label() {
        return label;
    }

    public boolean allowsFraction() {
        return allowsFraction;
    }

    public static UnitOfMeasure of(String code) {
        if (code == null) {
            throw new BusinessRuleException("unitCode", "Укажите единицу измерения");
        }
        try {
            return valueOf(code.strip().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new BusinessRuleException("unitCode", "Неизвестная единица измерения: " + code);
        }
    }

    public void validateQuantity(BigDecimal quantity) {
        if (quantity == null || quantity.signum() <= 0) {
            throw new BusinessRuleException("quantity", "Количество должно быть больше нуля");
        }
        if (!allowsFraction && quantity.stripTrailingZeros().scale() > 0) {
            throw new BusinessRuleException("quantity", "Для единицы «" + label + "» количество должно быть целым");
        }
        if (quantity.stripTrailingZeros().scale() > 3) {
            throw new BusinessRuleException("quantity", "Не больше трёх знаков после запятой");
        }
    }
}
