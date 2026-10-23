package com.company.acts.parts.domain;

/** Единица измерения: шт, л, кг, м, компл. Для жидкостей на розлив — литры с дробным количеством. */
public record Unit(String code, String name, boolean allowsFraction) {
}
