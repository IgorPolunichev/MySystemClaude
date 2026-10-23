-- Справочники заказчиков, техники и инженеров (раздел 5 SPEC.md).
-- Общие поля: UUID v7 (присваивается приложением), версия для оптимистичной блокировки, аудит, мягкое удаление.

CREATE EXTENSION IF NOT EXISTS pg_trgm;

CREATE TABLE organization (
    id             UUID         PRIMARY KEY,
    name           VARCHAR(300) NOT NULL,
    short_name     VARCHAR(150),
    inn            VARCHAR(12),
    kpp            VARCHAR(9),
    ogrn           VARCHAR(15),
    legal_address  VARCHAR(500),
    actual_address VARCHAR(500),
    bank_details   VARCHAR(1000),
    phone          VARCHAR(50),
    email          VARCHAR(150),
    notes          VARCHAR(2000),
    is_active      BOOLEAN      NOT NULL DEFAULT TRUE,
    version        BIGINT       NOT NULL,
    created_at     TIMESTAMPTZ  NOT NULL,
    created_by     VARCHAR(100),
    updated_at     TIMESTAMPTZ  NOT NULL,
    updated_by     VARCHAR(100),
    deleted_at     TIMESTAMPTZ
);
CREATE UNIQUE INDEX ux_organization_inn ON organization (inn) WHERE deleted_at IS NULL AND inn IS NOT NULL;
CREATE INDEX ix_organization_name_trgm ON organization USING gin (lower(name) gin_trgm_ops);

CREATE TABLE department (
    id              UUID         PRIMARY KEY,
    organization_id UUID         NOT NULL REFERENCES organization (id),
    parent_id       UUID         REFERENCES department (id),
    name            VARCHAR(200) NOT NULL,
    code            VARCHAR(50),
    version         BIGINT       NOT NULL,
    created_at      TIMESTAMPTZ  NOT NULL,
    created_by      VARCHAR(100),
    updated_at      TIMESTAMPTZ  NOT NULL,
    updated_by      VARCHAR(100),
    deleted_at      TIMESTAMPTZ
);
CREATE INDEX ix_department_organization ON department (organization_id) WHERE deleted_at IS NULL;

CREATE TABLE equipment_type (
    id         UUID         PRIMARY KEY,
    name       VARCHAR(100) NOT NULL,
    version    BIGINT       NOT NULL,
    created_at TIMESTAMPTZ  NOT NULL,
    created_by VARCHAR(100),
    updated_at TIMESTAMPTZ  NOT NULL,
    updated_by VARCHAR(100),
    deleted_at TIMESTAMPTZ
);
CREATE UNIQUE INDEX ux_equipment_type_name ON equipment_type (lower(name)) WHERE deleted_at IS NULL;

CREATE TABLE equipment (
    id                UUID          PRIMARY KEY,
    organization_id   UUID          NOT NULL REFERENCES organization (id),
    department_id     UUID          REFERENCES department (id),
    equipment_type_id UUID          NOT NULL REFERENCES equipment_type (id),
    brand             VARCHAR(100)  NOT NULL,
    model             VARCHAR(100)  NOT NULL,
    serial_number     VARCHAR(100),
    garage_number     VARCHAR(50),
    plate_number      VARCHAR(20),
    meter_type        VARCHAR(10)   NOT NULL CHECK (meter_type IN ('HOURS', 'KM', 'BOTH')),
    current_hours     NUMERIC(12, 1) CHECK (current_hours >= 0),
    current_km        NUMERIC(12, 1) CHECK (current_km >= 0),
    manufacture_year  INTEGER,
    notes             VARCHAR(2000),
    version           BIGINT        NOT NULL,
    created_at        TIMESTAMPTZ   NOT NULL,
    created_by        VARCHAR(100),
    updated_at        TIMESTAMPTZ   NOT NULL,
    updated_by        VARCHAR(100),
    deleted_at        TIMESTAMPTZ
);
CREATE UNIQUE INDEX ux_equipment_serial ON equipment (organization_id, serial_number)
    WHERE deleted_at IS NULL AND serial_number IS NOT NULL;
CREATE INDEX ix_equipment_organization ON equipment (organization_id) WHERE deleted_at IS NULL;
CREATE INDEX ix_equipment_department ON equipment (department_id) WHERE deleted_at IS NULL;
-- Поиск по части номера: lower(...) LIKE '%…%' использует триграммные индексы.
CREATE INDEX ix_equipment_serial_trgm ON equipment USING gin (lower(coalesce(serial_number, '')) gin_trgm_ops);
CREATE INDEX ix_equipment_garage_trgm ON equipment USING gin (lower(coalesce(garage_number, '')) gin_trgm_ops);
CREATE INDEX ix_equipment_plate_trgm ON equipment USING gin (lower(coalesce(plate_number, '')) gin_trgm_ops);

CREATE TABLE service_department (
    id         UUID         PRIMARY KEY,
    name       VARCHAR(200) NOT NULL,
    version    BIGINT       NOT NULL,
    created_at TIMESTAMPTZ  NOT NULL,
    created_by VARCHAR(100),
    updated_at TIMESTAMPTZ  NOT NULL,
    updated_by VARCHAR(100),
    deleted_at TIMESTAMPTZ
);
CREATE UNIQUE INDEX ux_service_department_name ON service_department (lower(name)) WHERE deleted_at IS NULL;

CREATE TABLE engineer (
    id                    UUID         PRIMARY KEY,
    keycloak_user_id      VARCHAR(64),
    username              VARCHAR(50)  NOT NULL,
    last_name             VARCHAR(100) NOT NULL,
    first_name            VARCHAR(100) NOT NULL,
    middle_name           VARCHAR(100),
    position              VARCHAR(150),
    service_department_id UUID         REFERENCES service_department (id),
    phone                 VARCHAR(50),
    email                 VARCHAR(150),
    is_active             BOOLEAN      NOT NULL DEFAULT TRUE,
    version               BIGINT       NOT NULL,
    created_at            TIMESTAMPTZ  NOT NULL,
    created_by            VARCHAR(100),
    updated_at            TIMESTAMPTZ  NOT NULL,
    updated_by            VARCHAR(100),
    deleted_at            TIMESTAMPTZ
);
CREATE UNIQUE INDEX ux_engineer_username ON engineer (username) WHERE deleted_at IS NULL;
CREATE UNIQUE INDEX ux_engineer_keycloak ON engineer (keycloak_user_id) WHERE deleted_at IS NULL AND keycloak_user_id IS NOT NULL;
