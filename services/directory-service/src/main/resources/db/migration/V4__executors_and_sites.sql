-- Исполнители (компании, от имени которых оказываются услуги) и производственные площадки заказчиков.

CREATE TABLE executor_company (
    id           UUID          PRIMARY KEY,
    name         VARCHAR(300)  NOT NULL,
    short_name   VARCHAR(150),
    inn          VARCHAR(12),
    kpp          VARCHAR(9),
    ogrn         VARCHAR(15),
    address      VARCHAR(500),
    phone        VARCHAR(50),
    email        VARCHAR(150),
    bank_details VARCHAR(1000),
    is_default   BOOLEAN       NOT NULL DEFAULT FALSE,
    is_active    BOOLEAN       NOT NULL DEFAULT TRUE,
    version      BIGINT        NOT NULL,
    created_at   TIMESTAMPTZ   NOT NULL,
    created_by   VARCHAR(100),
    updated_at   TIMESTAMPTZ   NOT NULL,
    updated_by   VARCHAR(100),
    deleted_at   TIMESTAMPTZ
);
CREATE UNIQUE INDEX ux_executor_default ON executor_company (is_default) WHERE is_default AND deleted_at IS NULL;
CREATE UNIQUE INDEX ux_executor_inn ON executor_company (inn) WHERE inn IS NOT NULL AND deleted_at IS NULL;

-- Основной исполнитель по умолчанию; администратор переименует его и укажет реквизиты.
INSERT INTO executor_company (id, name, short_name, is_default, is_active, version, created_at, created_by, updated_at, updated_by)
VALUES ('01970000-0000-7000-8000-000000000001', 'Общество с ограниченной ответственностью «Сервисная компания»',
        'ООО «Сервисная компания»', TRUE, TRUE, 0, now(), 'system', now(), 'system');

CREATE TABLE site (
    id              UUID          PRIMARY KEY,
    organization_id UUID          NOT NULL REFERENCES organization (id),
    name            VARCHAR(200)  NOT NULL,
    code            VARCHAR(6),
    address         VARCHAR(500),
    is_active       BOOLEAN       NOT NULL DEFAULT TRUE,
    version         BIGINT        NOT NULL,
    created_at      TIMESTAMPTZ   NOT NULL,
    created_by      VARCHAR(100),
    updated_at      TIMESTAMPTZ   NOT NULL,
    updated_by      VARCHAR(100),
    deleted_at      TIMESTAMPTZ
);
CREATE UNIQUE INDEX ux_site_name ON site (organization_id, lower(name)) WHERE deleted_at IS NULL;
CREATE UNIQUE INDEX ux_site_code ON site (organization_id, code) WHERE deleted_at IS NULL AND code IS NOT NULL;

ALTER TABLE equipment ADD COLUMN site_id UUID REFERENCES site (id);
CREATE INDEX ix_equipment_site ON equipment (site_id) WHERE deleted_at IS NULL;
