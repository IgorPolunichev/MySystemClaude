package com.company.acts.act.application.port.out;

/** Атомарная выдача следующего номера акта в пределах года (без дубликатов при одновременных запросах). */
public interface ActNumberGenerator {

    /**
     * Следующий номер. Если у площадки задан код, он входит в номер и у него свой счётчик:
     * «АВР-А-2026-000001»; иначе — «АВР-2026-000001».
     */
    String next(int year, String siteCode);
}
