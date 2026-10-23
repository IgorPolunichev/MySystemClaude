# ADR 0002. Maven вместо Gradle

- Статус: принято
- Дата: 2026-10-23

## Контекст

`SPEC.md` изначально предлагал Gradle с version catalog. Заказчику привычнее Maven.

## Решение

- Maven multi-module с Maven Wrapper (`./mvnw`, Maven 3.9.9) — установка Maven не нужна.
- Корневой `pom.xml` наследует `spring-boot-starter-parent` и играет роль BOM: все версии — в `<properties>` и `<dependencyManagement>`; в модулях версии не указываются.
- Интеграционные тесты называются `*IT` и запускаются `maven-failsafe-plugin` на фазе `verify`; без Docker их можно пропустить флагом `-DskipITs`.
- `SPEC.md` исправлен: Gradle заменён на Maven.

## Последствия

Сборка одного сервиса с зависимостями: `./mvnw -pl services/<name> -am package`. Так же собираются Docker-образы (единый `Dockerfile` с аргументом `SERVICE`).
