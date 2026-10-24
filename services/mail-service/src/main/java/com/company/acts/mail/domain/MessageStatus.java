package com.company.acts.mail.domain;

/** QUEUED — ждёт отправки; SENT — отправлено; FAILED — повторы исчерпаны; EML — сформировано для Outlook. */
public enum MessageStatus {
    QUEUED,
    SENT,
    FAILED,
    EML
}
