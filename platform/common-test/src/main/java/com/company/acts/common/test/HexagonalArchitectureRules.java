package com.company.acts.common.test;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;

/**
 * Правила гексагональной архитектуры (раздел 4 SPEC.md):
 * domain ничего не знает о Spring, application и adapter; application не знает об adapter.
 */
public final class HexagonalArchitectureRules {

    private HexagonalArchitectureRules() {
    }

    public static void check(String basePackage) {
        JavaClasses classes = new ClassFileImporter()
                .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
                .importPackages(basePackage);

        noClasses().that().resideInAPackage(basePackage + ".domain..")
                .should().dependOnClassesThat().resideInAnyPackage(
                        basePackage + ".application..", basePackage + ".adapter..", "org.springframework..",
                        "jakarta.persistence..")
                .allowEmptyShould(true)
                .because("домен должен быть чистым Java-кодом без фреймворков и адаптеров")
                .check(classes);

        noClasses().that().resideInAPackage(basePackage + ".application..")
                .should().dependOnClassesThat().resideInAPackage(basePackage + ".adapter..")
                .allowEmptyShould(true)
                .because("прикладной слой работает с адаптерами только через порты")
                .check(classes);
    }
}
