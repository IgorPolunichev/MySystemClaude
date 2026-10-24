package com.company.acts.directory.domain;

import com.company.acts.common.core.BusinessRuleException;
import com.company.acts.common.core.Strings;
import java.util.Objects;
import java.util.UUID;

/**
 * Производственная площадка организации-заказчика (например, ООО «Тест» — площадки А, Б, В). Площадка,
 * прекратившая работу, блокируется: её нельзя выбрать для техники и новых актов, но выданные акты её сохраняют.
 */
public record Site(UUID id, Long version, UUID organizationId, String name, String code, String address,
                   boolean active) {

    /** Код площадки в номере акта: 1–6 букв или цифр, например «А» или «01». */
    private static final java.util.regex.Pattern CODE = java.util.regex.Pattern.compile("[\\p{L}\\p{Nd}]{1,6}");

    public Site {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(organizationId, "organizationId");
        name = Strings.trimToNull(name);
        address = Strings.trimToNull(address);
        code = Strings.normalizeCode(code);
        if (code != null && !CODE.matcher(code).matches()) {
            throw new BusinessRuleException("code", "Код площадки — от 1 до 6 букв или цифр, например «А» или «01»");
        }
        if (name == null) {
            throw new BusinessRuleException("name", "Укажите название площадки");
        }
    }
}
