-- Рабочие графики (раздел 19.15 SPEC.md). График по дням не хранится: он вычисляется из шаблона, состава бригад,
-- смещений заезда и ручных отметок.

CREATE TABLE schedule_template (
    id                UUID         PRIMARY KEY,
    name              VARCHAR(200) NOT NULL,
    mode              VARCHAR(16)  NOT NULL CHECK (mode IN ('FIVE_TWO', 'ROTATION')),
    work_days         INTEGER      NOT NULL DEFAULT 5,
    off_days          INTEGER      NOT NULL DEFAULT 2,
    day_start         TIME         NOT NULL,
    day_end           TIME         NOT NULL,
    day_break         INTEGER      NOT NULL DEFAULT 0,
    night_start       TIME,
    night_end         TIME,
    night_break       INTEGER,
    switch_after_days INTEGER      NOT NULL DEFAULT 0,
    rest_after_nights BOOLEAN      NOT NULL DEFAULT FALSE,
    overlap_days      INTEGER      NOT NULL DEFAULT 0,
    day_priority      BOOLEAN      NOT NULL DEFAULT TRUE,
    updated_at        TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_by        VARCHAR(100)
);

-- Группа: отдел (service department) + участок (площадка) с шаблоном и датой начала.
CREATE TABLE schedule_group (
    id             UUID         PRIMARY KEY,
    name           VARCHAR(200) NOT NULL,
    department_id  UUID,
    site_id        UUID,
    site_name      VARCHAR(300),
    template_id    UUID         NOT NULL REFERENCES schedule_template (id),
    start_date     DATE         NOT NULL,
    required_day   INTEGER      NOT NULL DEFAULT 0,
    required_night INTEGER      NOT NULL DEFAULT 0,
    approved_at    TIMESTAMPTZ,
    approved_by    VARCHAR(100),
    version        BIGINT       NOT NULL DEFAULT 0,
    updated_at     TIMESTAMPTZ  NOT NULL DEFAULT now()
);
CREATE INDEX ix_schedule_group_department ON schedule_group (department_id);

CREATE TABLE schedule_member (
    group_id    UUID         NOT NULL REFERENCES schedule_group (id) ON DELETE CASCADE,
    employee_id UUID         NOT NULL,
    name        VARCHAR(200) NOT NULL,
    crew        VARCHAR(1)   NOT NULL CHECK (crew IN ('A', 'B')),
    track       VARCHAR(16)  NOT NULL,
    offset_days INTEGER      NOT NULL DEFAULT 0,
    partner_id  UUID,
    position    INTEGER      NOT NULL,
    PRIMARY KEY (group_id, employee_id)
);
CREATE INDEX ix_schedule_member_employee ON schedule_member (employee_id);

-- Ручные отметки: отпуск, больничный, разовое продление вахты (рабочий день вместо выходного) и т. п.
CREATE TABLE schedule_override (
    group_id    UUID         NOT NULL REFERENCES schedule_group (id) ON DELETE CASCADE,
    employee_id UUID         NOT NULL,
    day         DATE         NOT NULL,
    kind        VARCHAR(10)  NOT NULL CHECK (kind IN ('DAY', 'NIGHT', 'REST', 'OFF', 'VACATION', 'SICK')),
    comment     VARCHAR(300),
    updated_by  VARCHAR(100),
    PRIMARY KEY (group_id, employee_id, day)
);
