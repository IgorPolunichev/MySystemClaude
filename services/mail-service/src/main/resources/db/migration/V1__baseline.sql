-- Базовая схема сервиса: таблицы инфраструктуры событий (раздел 4 SPEC.md).
-- Бизнес-таблицы добавляются следующими миграциями на своих этапах.

-- Transactional outbox: событие пишется в одной транзакции с изменением данных,
-- отдельный публикатор отправляет его в RabbitMQ.
CREATE TABLE outbox (
    id            UUID         PRIMARY KEY,
    aggregate_id  UUID,
    event_type    VARCHAR(100) NOT NULL,
    event_version INTEGER      NOT NULL DEFAULT 1,
    payload       JSONB        NOT NULL,
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT now(),
    published_at  TIMESTAMPTZ
);
CREATE INDEX ix_outbox_unpublished ON outbox (created_at) WHERE published_at IS NULL;

-- Идемпотентность обработчиков: однажды обработанное событие повторно не применяется.
CREATE TABLE processed_event (
    event_id     UUID        PRIMARY KEY,
    processed_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
