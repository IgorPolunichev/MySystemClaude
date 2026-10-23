-- Каталог запчастей и единицы измерения (раздел 5 SPEC.md).

CREATE EXTENSION IF NOT EXISTS pg_trgm;

CREATE TABLE unit_of_measure (
    code            VARCHAR(10) PRIMARY KEY,
    name_ru         VARCHAR(20) NOT NULL,
    allows_fraction BOOLEAN     NOT NULL,
    sort_order      INTEGER     NOT NULL
);

INSERT INTO unit_of_measure (code, name_ru, allows_fraction, sort_order) VALUES
    ('PCS', 'шт',    FALSE, 1),
    ('L',   'л',     TRUE,  2),
    ('KG',  'кг',    TRUE,  3),
    ('M',   'м',     TRUE,  4),
    ('SET', 'компл.', FALSE, 5);

CREATE TABLE part_catalog_item (
    id             UUID         PRIMARY KEY,
    name           VARCHAR(300) NOT NULL,
    catalog_number VARCHAR(100),
    manufacturer   VARCHAR(100),
    default_unit   VARCHAR(10)  NOT NULL REFERENCES unit_of_measure (code),
    is_fluid       BOOLEAN      NOT NULL DEFAULT FALSE,
    notes          VARCHAR(2000),
    version        BIGINT       NOT NULL,
    created_at     TIMESTAMPTZ  NOT NULL,
    created_by     VARCHAR(100),
    updated_at     TIMESTAMPTZ  NOT NULL,
    updated_by     VARCHAR(100),
    deleted_at     TIMESTAMPTZ
);
CREATE UNIQUE INDEX ux_part_number_manufacturer ON part_catalog_item (catalog_number, lower(coalesce(manufacturer, '')))
    WHERE deleted_at IS NULL AND catalog_number IS NOT NULL;
CREATE INDEX ix_part_name_trgm ON part_catalog_item USING gin (lower(name) gin_trgm_ops);
CREATE INDEX ix_part_number_trgm ON part_catalog_item USING gin (lower(coalesce(catalog_number, '')) gin_trgm_ops);
