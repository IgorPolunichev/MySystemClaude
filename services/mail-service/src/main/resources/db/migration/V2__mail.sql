-- Адресная книга, шаблоны писем, письма и вложения (раздел 10 SPEC.md).

CREATE TABLE contact (
    id              UUID          PRIMARY KEY,
    full_name       VARCHAR(200),
    email           VARCHAR(254)  NOT NULL,
    organization_id UUID,
    organization_name VARCHAR(300),
    position        VARCHAR(200),
    notes           VARCHAR(500),
    is_default_for_organization BOOLEAN NOT NULL DEFAULT FALSE,
    is_active       BOOLEAN       NOT NULL DEFAULT TRUE,
    created_at      TIMESTAMPTZ   NOT NULL DEFAULT now(),
    created_by      VARCHAR(100),
    updated_at      TIMESTAMPTZ   NOT NULL DEFAULT now()
);
CREATE UNIQUE INDEX ux_contact_email ON contact (lower(email));
CREATE INDEX ix_contact_org ON contact (organization_id) WHERE is_active;

CREATE TABLE contact_group (
    id         UUID         PRIMARY KEY,
    name       VARCHAR(200) NOT NULL,
    created_at TIMESTAMPTZ  NOT NULL DEFAULT now()
);
CREATE UNIQUE INDEX ux_contact_group_name ON contact_group (lower(name));

CREATE TABLE contact_group_member (
    group_id   UUID NOT NULL REFERENCES contact_group (id) ON DELETE CASCADE,
    contact_id UUID NOT NULL REFERENCES contact (id) ON DELETE CASCADE,
    PRIMARY KEY (group_id, contact_id)
);

CREATE TABLE email_template (
    id               UUID          PRIMARY KEY,
    name             VARCHAR(200)  NOT NULL,
    subject_template VARCHAR(1000) NOT NULL,
    body_template    TEXT          NOT NULL,
    is_default       BOOLEAN       NOT NULL DEFAULT FALSE,
    updated_at       TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_by       VARCHAR(100)
);
CREATE UNIQUE INDEX ux_email_template_default ON email_template (is_default) WHERE is_default;

-- Письмо: статусы QUEUED (ждёт отправки), SENT, FAILED (повторы исчерпаны), EML (сформировано для Outlook).
CREATE TABLE email_message (
    id              UUID          PRIMARY KEY,
    template_id     UUID,
    subject         VARCHAR(1000) NOT NULL,
    body            TEXT          NOT NULL,
    recipients_to   TEXT          NOT NULL,
    recipients_cc   TEXT,
    recipients_bcc  TEXT,
    reply_to        VARCHAR(254),
    act_ids         TEXT          NOT NULL,
    act_numbers     TEXT,
    mode            VARCHAR(10)   NOT NULL CHECK (mode IN ('SMTP', 'EML')),
    status          VARCHAR(10)   NOT NULL CHECK (status IN ('QUEUED', 'SENT', 'FAILED', 'EML')),
    attempts        INTEGER       NOT NULL DEFAULT 0,
    next_attempt_at TIMESTAMPTZ,
    error           VARCHAR(2000),
    created_at      TIMESTAMPTZ   NOT NULL DEFAULT now(),
    created_by      VARCHAR(100),
    sent_at         TIMESTAMPTZ
);
CREATE INDEX ix_email_message_queue ON email_message (next_attempt_at) WHERE status = 'QUEUED';
CREATE INDEX ix_email_message_created ON email_message (created_at DESC);

CREATE TABLE email_attachment (
    id           UUID          PRIMARY KEY,
    message_id   UUID          NOT NULL REFERENCES email_message (id) ON DELETE CASCADE,
    file_name    VARCHAR(300)  NOT NULL,
    content_type VARCHAR(150)  NOT NULL,
    content      BYTEA         NOT NULL,
    position     INTEGER       NOT NULL
);
CREATE INDEX ix_email_attachment_message ON email_attachment (message_id, position);

-- Шаблон по умолчанию (раздел 10): тема с номерами актов, в теле — таблица актов.
INSERT INTO email_template (id, name, subject_template, body_template, is_default, updated_by) VALUES (
    '01990000-0000-7000-8000-000000000001',
    'Акты выполненных работ',
    'Акты выполненных работ № <#list acts as a>${a.number}<#sep>, </#list>',
    '<p>Добрый день!</p>
<p>Направляем акты выполненных работ:</p>
<table border="1" cellpadding="6" cellspacing="0" style="border-collapse:collapse">
<tr><th>Номер</th><th>Период работ</th><th>Техника</th><th>Работы</th><th>Часы</th></tr>
<#list acts as a>
<tr><td>${a.number}</td><td>${a.period}</td><td>${a.equipment}</td><td>${a.shortDescription}</td><td>${a.totalHours}</td></tr>
</#list>
</table>
<p>Всего часов: ${totalHours}</p>
<p>С уважением,<br>${sender.fullName}<#if sender.position?has_content>, ${sender.position}</#if><#if sender.phone?has_content><br>тел. ${sender.phone}</#if></p>',
    TRUE, 'system');
