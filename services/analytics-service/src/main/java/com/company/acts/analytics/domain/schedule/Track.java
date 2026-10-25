package com.company.acts.analytics.domain.schedule;

/**
 * Как сотрудник работает внутри своих рабочих дней при ночных сменах: сначала днём, потом ночью (или наоборот) —
 * подгруппы меняются местами через заданное число дней; либо всю вахту только днём или только ночью.
 */
public enum Track {
    DAY_FIRST,
    NIGHT_FIRST,
    FIXED_DAY,
    FIXED_NIGHT
}
