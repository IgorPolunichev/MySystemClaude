package com.company.acts.analytics.application.port.out;

import java.time.LocalDate;
import java.util.Map;

/** Исключения производственного календаря: праздники (false), рабочие выходные (true). */
public interface CalendarStore {

    Map<LocalDate, Boolean> overrides(LocalDate from, LocalDate to);

    /** working = null — убрать исключение (день по общему правилу пн–пт). */
    void set(LocalDate day, Boolean working, String comment);
}
