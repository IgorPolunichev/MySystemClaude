package com.company.acts.directory.domain;

import com.company.acts.common.core.BusinessRuleException;
import com.company.acts.common.core.RequisitesValidator;
import com.company.acts.common.core.Strings;
import java.util.Objects;
import java.util.UUID;

/** Организация-заказчик. Реквизиты проверяются при создании и изменении. */
public record Organization(
        UUID id,
        Long version,
        String name,
        String shortName,
        String inn,
        String kpp,
        String ogrn,
        String legalAddress,
        String actualAddress,
        String bankDetails,
        String phone,
        String email,
        String notes,
        boolean active) {

    public Organization {
        Objects.requireNonNull(id, "id");
        name = Strings.trimToNull(name);
        shortName = Strings.trimToNull(shortName);
        inn = Strings.trimToNull(inn);
        kpp = Strings.normalizeCode(kpp);
        ogrn = Strings.trimToNull(ogrn);
        legalAddress = Strings.trimToNull(legalAddress);
        actualAddress = Strings.trimToNull(actualAddress);
        bankDetails = Strings.trimToNull(bankDetails);
        phone = Strings.trimToNull(phone);
        email = Strings.trimToNull(email);
        notes = Strings.trimToNull(notes);
        if (name == null) {
            throw new BusinessRuleException("name", "Укажите наименование организации");
        }
        if (inn != null && !RequisitesValidator.isValidInn(inn)) {
            throw new BusinessRuleException("inn", "ИНН должен состоять из 10 или 12 цифр с верной контрольной суммой");
        }
        if (kpp != null && !RequisitesValidator.isValidKpp(kpp)) {
            throw new BusinessRuleException("kpp", "КПП должен состоять из 9 символов");
        }
        if (ogrn != null && !RequisitesValidator.isValidOgrn(ogrn)) {
            throw new BusinessRuleException("ogrn", "ОГРН должен состоять из 13 или 15 цифр с верной контрольной суммой");
        }
    }

    /** Название для списков и печати: сокращённое, если задано. */
    public String displayName() {
        return shortName != null ? shortName : name;
    }
}
