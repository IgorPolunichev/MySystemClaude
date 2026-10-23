package com.company.acts.common.test;

import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * Базовый класс интеграционных тестов сервисов с PostgreSQL. Контейнер один на JVM и переиспользуется
 * всеми тестами; Spring Boot подключается к нему через {@link ServiceConnection}. Требуется Docker.
 */
@Testcontainers
public abstract class PostgresIntegrationTest {

    public static final String POSTGRES_IMAGE = "postgres:16.4";

    @Container
    @ServiceConnection
    protected static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer(POSTGRES_IMAGE);
}
