package com.company.acts.directory.domain;

/** Как учитывается наработка техники: моточасы, пробег или оба показателя. */
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
