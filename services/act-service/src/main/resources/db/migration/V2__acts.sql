-- Ядро актов (раздел 5 SPEC.md): шапка со снимками справочников, участники, табель, запчасти, журнал, нумерация.

CREATE TABLE work_act (
    id                    UUID          PRIMARY KEY,
    number                VARCHAR(40),
    status                VARCHAR(16)   NOT NULL CHECK (status IN ('DRAFT', 'OPEN', 'CLOSED', 'CANCELLED')),
    -- Снимок организации и подразделения на момент работ
    org_id                UUID,
    org_name              VARCHAR(300),
    org_short_name        VARCHAR(150),
    org_inn               VARCHAR(12),
    org_kpp               VARCHAR(9),
    org_legal_address     VARCHAR(500),
    department_id         UUID,
    department_name       VARCHAR(200),
    -- Снимок техники
    equipment_id          UUID,
    equipment_type_name   VARCHAR(100),
    equipment_brand       VARCHAR(100),
    equipment_model       VARCHAR(100),
    equipment_serial      VARCHAR(100),
    equipment_garage      VARCHAR(50),
    equipment_plate       VARCHAR(20),
    equipment_meter_type  VARCHAR(10)   CHECK (equipment_meter_type IN ('HOURS', 'KM', 'BOTH')),
    meter_hours           NUMERIC(12, 1),
    meter_km              NUMERIC(12, 1),
    location              VARCHAR(300),
    work_start_override   DATE,
    work_end_override     DATE,
    short_description     VARCHAR(100),
    detailed_description  TEXT,
    recommendations       TEXT,
    customer_rep_name     VARCHAR(200),
    customer_rep_position VARCHAR(200),
    hide_requisites       BOOLEAN       NOT NULL DEFAULT TRUE,
    hide_travel_time      BOOLEAN       NOT NULL DEFAULT TRUE,
    author_engineer_id    UUID          NOT NULL,
    author_user_id        VARCHAR(64),
    closed_at             TIMESTAMPTZ,
    closed_by             VARCHAR(100),
    -- Итоги для реестра; пересчитываются при изменении времени и участников
    period_start          DATE,
    period_end            DATE,
    total_hours           NUMERIC(8, 2) NOT NULL DEFAULT 0,
    engineer_names        VARCHAR(2000),
    version               BIGINT        NOT NULL,
    created_at            TIMESTAMPTZ   NOT NULL,
    created_by            VARCHAR(100),
    updated_at            TIMESTAMPTZ   NOT NULL,
    updated_by            VARCHAR(100),
    deleted_at            TIMESTAMPTZ
);
CREATE UNIQUE INDEX ux_work_act_number ON work_act (number) WHERE number IS NOT NULL;
CREATE INDEX ix_work_act_status ON work_act (status);
CREATE INDEX ix_work_act_org ON work_act (org_id);
CREATE INDEX ix_work_act_equipment ON work_act (equipment_id);
CREATE INDEX ix_work_act_period ON work_act (period_start, period_end);
CREATE INDEX ix_work_act_created ON work_act (created_at DESC);

CREATE TABLE act_participant (
    id               UUID         PRIMARY KEY,
    act_id           UUID         NOT NULL REFERENCES work_act (id) ON DELETE CASCADE,
    engineer_id      UUID         NOT NULL,
    keycloak_user_id VARCHAR(64),
    full_name        VARCHAR(200) NOT NULL,
    position         VARCHAR(150),
    role             VARCHAR(10)  NOT NULL CHECK (role IN ('LEAD', 'MEMBER')),
    created_at       TIMESTAMPTZ  NOT NULL DEFAULT now(),
    UNIQUE (act_id, engineer_id)
);
CREATE INDEX ix_participant_engineer ON act_participant (engineer_id);
CREATE INDEX ix_participant_user ON act_participant (keycloak_user_id);

CREATE TABLE time_entry (
    id           UUID         PRIMARY KEY,
    act_id       UUID         NOT NULL REFERENCES work_act (id) ON DELETE CASCADE,
    engineer_id  UUID         NOT NULL,
    work_date    DATE         NOT NULL,
    hours        NUMERIC(5, 2) NOT NULL CHECK (hours >= 0 AND hours <= 24),
    travel_hours NUMERIC(5, 2) CHECK (travel_hours >= 0 AND travel_hours <= 24),
    comment      VARCHAR(500),
    version      BIGINT       NOT NULL,
    created_at   TIMESTAMPTZ  NOT NULL,
    created_by   VARCHAR(100),
    updated_at   TIMESTAMPTZ  NOT NULL,
    updated_by   VARCHAR(100),
    deleted_at   TIMESTAMPTZ,
    UNIQUE (act_id, engineer_id, work_date)
);
CREATE INDEX ix_time_entry_engineer_date ON time_entry (engineer_id, work_date);

CREATE TABLE act_part_line (
    id              UUID           PRIMARY KEY,
    act_id          UUID           NOT NULL REFERENCES work_act (id) ON DELETE CASCADE,
    position_no     INTEGER        NOT NULL,
    source          VARCHAR(10)    NOT NULL CHECK (source IN ('CATALOG', 'MANUAL', 'WAREHOUSE')),
    catalog_item_id UUID,
    name            VARCHAR(300)   NOT NULL,
    catalog_number  VARCHAR(100),
    quantity        NUMERIC(12, 3) NOT NULL CHECK (quantity > 0),
    unit_code       VARCHAR(10)    NOT NULL,
    notes           VARCHAR(500),
    version         BIGINT         NOT NULL,
    created_at      TIMESTAMPTZ    NOT NULL,
    created_by      VARCHAR(100),
    updated_at      TIMESTAMPTZ    NOT NULL,
    updated_by      VARCHAR(100),
    deleted_at      TIMESTAMPTZ
);
CREATE INDEX ix_part_line_act ON act_part_line (act_id, position_no);
CREATE INDEX ix_part_line_number ON act_part_line (catalog_number);

CREATE TABLE act_history (
    id         UUID         PRIMARY KEY,
    act_id     UUID         NOT NULL REFERENCES work_act (id) ON DELETE CASCADE,
    changed_at TIMESTAMPTZ  NOT NULL,
    changed_by VARCHAR(100),
    action     VARCHAR(40)  NOT NULL,
    details    VARCHAR(1000)
);
CREATE INDEX ix_act_history_act ON act_history (act_id, changed_at);

CREATE TABLE act_number_sequence (
    year       INTEGER     NOT NULL,
    prefix     VARCHAR(20) NOT NULL,
    last_value BIGINT      NOT NULL,
    PRIMARY KEY (year, prefix)
);
