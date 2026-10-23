package com.company.acts.common.test;

import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * Базовый класс интеграционных тестов сервисов с PostgreSQL. Требуется Docker.
 *
 * <p>Контейнер — один на всю JVM (паттерн singleton container): запускается при первой загрузке класса и
 * не останавливается между тестовыми классами. Это важно, потому что Spring кэширует контекст приложения
 * между тестовыми классами: если бы контейнер перезапускался для каждого класса, закэшированный контекст
 * продолжал бы ходить на старый порт. Адрес базы передаётся в Spring через {@link DynamicPropertySource}.
 * Контейнер удаляет Testcontainers (Ryuk) после завершения JVM.
 */
public abstract class PostgresIntegrationTest {

    public static final String POSTGRES_IMAGE = "postgres:16.4";

    protected static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer(POSTGRES_IMAGE);

    static {
        POSTGRES.start();
    }

    @DynamicPropertySource
    static void datasource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }
}
