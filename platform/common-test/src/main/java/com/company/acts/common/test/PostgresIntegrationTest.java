package com.company.acts.common.test;

import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * Базовый класс интеграционных тестов сервисов с PostgreSQL. Требуется Docker.
 *
 * <p>Контейнер — один на всю JVM (паттерн singleton container): запускается при первой загрузке класса и
 * не останавливается между тестовыми классами. Это важно, потому что Spring кэширует контекст приложения
 * между тестовыми классами: если бы контейнер перезапускался для каждого класса (как с {@code @Container}),
 * закэшированный контекст продолжал бы ходить на старый порт. Контейнер удаляет Testcontainers (Ryuk)
 * после завершения JVM.
 */
public abstract class PostgresIntegrationTest {

    public static final String POSTGRES_IMAGE = "postgres:16.4";

    @ServiceConnection
    protected static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer(POSTGRES_IMAGE);

    static {
        POSTGRES.start();
    }
}
