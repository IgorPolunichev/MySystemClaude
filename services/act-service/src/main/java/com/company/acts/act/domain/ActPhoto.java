package com.company.acts.act.domain;

import com.company.acts.common.core.BusinessRuleException;
import com.company.acts.common.core.Strings;
import java.util.Objects;
import java.util.UUID;

/**
 * Фото технического отчёта: файл в media-service и подпись. Подпись обязательна для закрытия акта, но фото
 * можно сначала загрузить и подписать позже — на объекте так удобнее.
 */
public record ActPhoto(UUID id, UUID actId, UUID mediaId, String caption, int position) {

    public static final int MAX_CAPTION = 200;

    public ActPhoto {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(actId, "actId");
        if (mediaId == null) {
            throw new BusinessRuleException("mediaId", "Не указан файл фото");
        }
        caption = Strings.trimToNull(caption);
        if (caption != null && caption.length() > MAX_CAPTION) {
            throw new BusinessRuleException("caption", "Подпись — не длиннее " + MAX_CAPTION + " символов");
        }
    }

    public boolean hasCaption() {
        return caption != null;
    }
}
