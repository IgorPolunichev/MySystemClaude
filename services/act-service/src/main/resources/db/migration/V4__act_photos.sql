-- Фото технического отчёта (раздел 6.6 SPEC.md): файл — в media-service, здесь подпись и порядок.
CREATE TABLE act_photo (
    id          UUID         PRIMARY KEY,
    act_id      UUID         NOT NULL REFERENCES work_act (id) ON DELETE CASCADE,
    media_id    UUID         NOT NULL,
    caption     VARCHAR(200),
    position_no INTEGER      NOT NULL,
    version     BIGINT       NOT NULL,
    created_at  TIMESTAMPTZ  NOT NULL,
    created_by  VARCHAR(100),
    updated_at  TIMESTAMPTZ  NOT NULL,
    updated_by  VARCHAR(100),
    deleted_at  TIMESTAMPTZ,
    UNIQUE (act_id, media_id)
);
CREATE INDEX ix_act_photo_act ON act_photo (act_id, position_no);
