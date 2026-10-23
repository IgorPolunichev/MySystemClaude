# ADR 0003. Java 21 и Spring Boot 4.0

- Статус: принято (заменяет решение этапа 0 № 4 о Java 25)
- Дата: 2026-10-23

## Контекст

На этапе 0 было записано «Java 25 LTS и Spring Boot 4.x». Заказчик предпочитает Java 21; `SPEC.md` тоже указывает Java 21.

## Решение

- Java 21 (LTS), виртуальные потоки включены (`spring.threads.virtual.enabled=true`).
- Spring Boot 4.0.7 и Spring Cloud 2025.1.2 (Oakwood) — совместимая пара, обе версии выпущены больше месяца назад. Spring Boot 4 работает на Java 17+, так что Java 21 подходит.
- Используются стартеры Spring Boot 4 с новыми именами: `spring-boot-starter-webmvc`, `spring-boot-starter-security-oauth2-resource-server`, `spring-boot-starter-flyway`.
- Шлюз — `spring-cloud-starter-gateway-server-webflux`, маршруты в `spring.cloud.gateway.server.webflux.routes`.

## Последствия

Переход на Java 25 позже — смена `java.version` в корневом `pom.xml` и базовых образов в `Dockerfile`; код менять не нужно.
