package com.company.acts.act.domain;

/** Учёт наработки техники (снимок из справочника). */
public enum MeterType {
    HOURS,
    KM,
    BOTH;

    public boolean usesHours() {
        return this != KM;
    }

    public boolean usesKm() {
        return this != HOURS;
    }
}
