-- Исполнитель и производственная площадка заказчика в акте (снимки), документы акта (сканы и другие файлы).
-- Код площадки входит в номер акта: АВР-А-2026-000001 (у каждого кода свой счётчик в act_number_sequence).

ALTER TABLE work_act
    ADD COLUMN executor_id         UUID,
    ADD COLUMN executor_name       VARCHAR(300),
    ADD COLUMN executor_short_name VARCHAR(150),
    ADD COLUMN executor_inn        VARCHAR(12),
    ADD COLUMN executor_kpp        VARCHAR(9),
    ADD COLUMN executor_address    VARCHAR(500),
    ADD COLUMN executor_phone      VARCHAR(50),
    ADD COLUMN site_id             UUID,
    ADD COLUMN site_name           VARCHAR(200),
    ADD COLUMN site_code           VARCHAR(6),
    ADD COLUMN site_address        VARCHAR(500),
    ADD COLUMN has_signed_scan     BOOLEAN NOT NULL DEFAULT FALSE;

CREATE TABLE act_attachment (
    id           UUID          PRIMARY KEY,
    act_id       UUID          NOT NULL REFERENCES work_act (id) ON DELETE CASCADE,
    media_id     UUID          NOT NULL,
    kind         VARCHAR(20)   NOT NULL CHECK (kind IN ('SIGNED_ACT', 'OTHER')),
    file_name    VARCHAR(300),
    content_type VARCHAR(100),
    size_bytes   BIGINT        NOT NULL DEFAULT 0,
    comment      VARCHAR(300),
    uploaded_by  VARCHAR(100),
    uploaded_at  TIMESTAMPTZ   NOT NULL,
    UNIQUE (act_id, media_id)
);
CREATE INDEX ix_act_attachment_act ON act_attachment (act_id, uploaded_at);
