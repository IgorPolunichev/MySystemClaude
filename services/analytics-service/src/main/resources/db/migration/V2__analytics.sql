-- Read model продуктивных часов (раздел 9 SPEC.md) и нормы. Строится из событий act-service; полная пересборка —
-- POST /api/v1/analytics/rebuild.

CREATE TABLE fact_act (
    act_id          UUID         PRIMARY KEY,
    number          VARCHAR(40),
    status          VARCHAR(16)  NOT NULL,
    organization_id UUID,
    equipment_id    UUID,
    site_id         UUID,
    updated_at      TIMESTAMPTZ  NOT NULL DEFAULT now()
);
CREATE INDEX ix_fact_act_status ON fact_act (status);

CREATE TABLE fact_time (
    act_id       UUID    NOT NULL,
    engineer_id  UUID    NOT NULL,
    work_date    DATE    NOT NULL,
    work_minutes INTEGER NOT NULL CHECK (work_minutes BETWEEN 0 AND 1440),
    PRIMARY KEY (act_id, engineer_id, work_date)
);
CREATE INDEX ix_fact_time_engineer_date ON fact_time (engineer_id, work_date);

CREATE TABLE norm_engineer (
    engineer_id UUID          NOT NULL,
    month       DATE          NOT NULL,
    hours       NUMERIC(6, 2) NOT NULL CHECK (hours >= 0),
    updated_by  VARCHAR(100),
    updated_at  TIMESTAMPTZ   NOT NULL DEFAULT now(),
    PRIMARY KEY (engineer_id, month)
);

CREATE TABLE norm_department (
    department_id UUID          NOT NULL,
    month         DATE          NOT NULL,
    hours         NUMERIC(7, 2) NOT NULL CHECK (hours >= 0),
    updated_by    VARCHAR(100),
    updated_at    TIMESTAMPTZ   NOT NULL DEFAULT now(),
    PRIMARY KEY (department_id, month)
);

-- Исключения производственного календаря: по умолчанию рабочие дни — пн–пт.
CREATE TABLE calendar_day (
    day        DATE         PRIMARY KEY,
    is_working BOOLEAN      NOT NULL,
    comment    VARCHAR(200)
);

-- Праздничные дни 2026 года по производственному календарю РФ. Администратору стоит сверить их с официальным
-- календарём и при необходимости поправить на экране «Календарь».
INSERT INTO calendar_day (day, is_working, comment) VALUES
    ('2026-01-01', FALSE, 'Новогодние каникулы'), ('2026-01-02', FALSE, 'Новогодние каникулы'),
    ('2026-01-05', FALSE, 'Новогодние каникулы'), ('2026-01-06', FALSE, 'Новогодние каникулы'),
    ('2026-01-07', FALSE, 'Рождество Христово'), ('2026-01-08', FALSE, 'Новогодние каникулы'),
    ('2026-01-09', FALSE, 'Перенос выходного'), ('2026-02-23', FALSE, 'День защитника Отечества'),
    ('2026-03-09', FALSE, 'Международный женский день (перенос)'), ('2026-05-01', FALSE, 'Праздник Весны и Труда'),
    ('2026-05-11', FALSE, 'День Победы (перенос)'), ('2026-06-12', FALSE, 'День России'),
    ('2026-11-04', FALSE, 'День народного единства'), ('2026-12-31', FALSE, 'Перенос выходного')
ON CONFLICT (day) DO NOTHING;
