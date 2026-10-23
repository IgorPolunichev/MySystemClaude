package com.company.acts.act.application.port.out;

import java.util.UUID;

/** Каталог запчастей (parts-service). */
public interface PartsClient {

    /** Добавить позицию, введённую вручную, в каталог; возвращает её id. */
    UUID createCatalogItem(String name, String catalogNumber, String unitCode, boolean fluid);
}
