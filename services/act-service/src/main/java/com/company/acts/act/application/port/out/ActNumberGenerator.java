package com.company.acts.act.application.port.out;

/** Атомарная выдача следующего номера акта в пределах года (без дубликатов при одновременных запросах). */
public interface ActNumberGenerator {

    String next(int year);
}
