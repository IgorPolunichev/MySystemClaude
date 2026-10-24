-- Отметка «Отправлен» (раздел 10 SPEC.md): по событию EmailSent от mail-service.
ALTER TABLE work_act
    ADD COLUMN last_sent_at TIMESTAMPTZ,
    ADD COLUMN sent_count   INTEGER NOT NULL DEFAULT 0;
