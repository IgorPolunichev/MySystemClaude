-- Табель: продуктивное время считается из интервала работы минус перерывы (раздел 6.4 SPEC.md).
-- Пример: 10:00–13:00, обед 11:00–12:00 → 120 минут = 2 ч. Источник правды — work_minutes;
-- hours = round(work_minutes / 60, 2) хранится для отчётов.

ALTER TABLE time_entry
    ADD COLUMN start_time   TIME,
    ADD COLUMN end_time     TIME,
    ADD COLUMN work_minutes INTEGER;

-- Записи, введённые до появления интервалов: интервал 09:00 + столько же часов, перерывов нет.
-- TIME + INTERVAL в PostgreSQL переходит через полночь, 24 ч дают окончание, равное началу (= сутки).
UPDATE time_entry
SET work_minutes = round(hours * 60)::INTEGER,
    start_time   = CASE WHEN hours > 0 THEN TIME '09:00' END,
    end_time     = CASE WHEN hours > 0 THEN TIME '09:00' + make_interval(mins => round(hours * 60)::INTEGER) END;

ALTER TABLE time_entry
    ALTER COLUMN work_minutes SET NOT NULL,
    ADD CONSTRAINT ck_time_entry_minutes CHECK (work_minutes BETWEEN 0 AND 1440),
    ADD CONSTRAINT ck_time_entry_interval CHECK ((start_time IS NULL) = (end_time IS NULL));

CREATE TABLE time_entry_break (
    id         UUID    PRIMARY KEY,
    entry_id   UUID    NOT NULL REFERENCES time_entry (id) ON DELETE CASCADE,
    position   INTEGER NOT NULL,
    start_time TIME    NOT NULL,
    end_time   TIME    NOT NULL
);
CREATE INDEX ix_time_entry_break_entry ON time_entry_break (entry_id, position);

UPDATE work_act w
SET total_hours = round(coalesce((SELECT sum(t.work_minutes) FROM time_entry t WHERE t.act_id = w.id), 0) / 60.0, 2);
