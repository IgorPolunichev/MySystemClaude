-- Метаданные файлов; сами файлы — в S3-хранилище (раздел 5 SPEC.md).
CREATE TABLE media_object (
    id                UUID          PRIMARY KEY,
    owner_type        VARCHAR(30)   NOT NULL,
    owner_id          UUID          NOT NULL,
    original_filename VARCHAR(300),
    content_type      VARCHAR(100)  NOT NULL,
    size_bytes        BIGINT        NOT NULL,
    width             INTEGER       NOT NULL,
    height            INTEGER       NOT NULL,
    sha256            VARCHAR(64)   NOT NULL,
    object_key        VARCHAR(500)  NOT NULL,
    thumbnail_key     VARCHAR(500)  NOT NULL,
    created_by        VARCHAR(100),
    created_at        TIMESTAMPTZ   NOT NULL,
    deleted_at        TIMESTAMPTZ
);
CREATE INDEX ix_media_owner ON media_object (owner_type, owner_id);
