package com.company.acts.common.core;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class RequisitesValidatorTest {

    @Test
    void validatesInn() {
        assertThat(RequisitesValidator.isValidInn("7707083893")).isTrue();   // ПАО Сбербанк
        assertThat(RequisitesValidator.isValidInn("7707083894")).isFalse();
        assertThat(RequisitesValidator.isValidInn("500100732259")).isTrue(); // ИНН физлица, пример ФНС
        assertThat(RequisitesValidator.isValidInn("500100732258")).isFalse();
        assertThat(RequisitesValidator.isValidInn("12345")).isFalse();
        assertThat(RequisitesValidator.isValidInn(null)).isFalse();
    }

    @Test
    void validatesKpp() {
        assertThat(RequisitesValidator.isValidKpp("773601001")).isTrue();
        assertThat(RequisitesValidator.isValidKpp("7736AB001")).isTrue();
        assertThat(RequisitesValidator.isValidKpp("77360100")).isFalse();
    }

    @Test
    void validatesOgrn() {
        assertThat(RequisitesValidator.isValidOgrn("1027700132195")).isTrue(); // ПАО Сбербанк
        assertThat(RequisitesValidator.isValidOgrn("1027700132196")).isFalse();
        assertThat(RequisitesValidator.isValidOgrn("304500116000157")).isTrue(); // ОГРНИП, пример
    }

    @Test
    void uuid7IsVersion7AndOrdered() throws InterruptedException {
        var first = Uuid7.next();
        Thread.sleep(2);
        var second = Uuid7.next();
        assertThat(first.version()).isEqualTo(7);
        assertThat(first.variant()).isEqualTo(2);
        assertThat(first.toString().compareTo(second.toString())).isNegative();
    }

    @Test
    void parsesSort() {
        assertThat(PageQuery.of(null, null, "-name")).isEqualTo(new PageQuery(0, 50, "name", false));
        assertThat(PageQuery.of(2, 500, "inn")).isEqualTo(new PageQuery(2, 200, "inn", true));
        assertThat(PageQuery.of(-1, 0, " ")).isEqualTo(new PageQuery(0, 50, null, true));
    }
}
