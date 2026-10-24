package com.company.acts.directory.domain;

import com.company.acts.common.core.BusinessRuleException;
import com.company.acts.common.core.RequisitesValidator;
import com.company.acts.common.core.Strings;
import java.util.Objects;
import java.util.UUID;

/**
 * Исполнитель — компания, от имени которой оказываются услуги (одна из ваших компаний). Один исполнитель
 * помечается основным и подставляется в новые акты. Заблокированного нельзя выбрать в акте.
 */
public record Executor(UUID id, Long version, String name, String shortName, String inn, String kpp, String ogrn,
                       String address, String phone, String email, String bankDetails, boolean isDefault,
                       boolean active) {

    public Executor {
        Objects.requireNonNull(id, "id");
        name = Strings.trimToNull(name);
        shortName = Strings.trimToNull(shortName);
        inn = Strings.trimToNull(inn);
        kpp = Strings.normalizeCode(kpp);
        ogrn = Strings.trimToNull(ogrn);
        address = Strings.trimToNull(address);
        phone = Strings.trimToNull(phone);
        email = Strings.trimToNull(email);
        bankDetails = Strings.trimToNull(bankDetails);
        if (name == null) {
            throw new BusinessRuleException("name", "Укажите наименование исполнителя");
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
        if (isDefault && !active) {
            throw new BusinessRuleException("active", "Основного исполнителя нельзя заблокировать — сначала назначьте другого");
        }
    }
}
