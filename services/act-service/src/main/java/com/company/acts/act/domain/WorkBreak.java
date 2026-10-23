package com.company.acts.act.domain;

import java.time.LocalTime;

/** Перерыв внутри рабочего интервала (обед, ожидание): не входит в продуктивное время. */
public record WorkBreak(LocalTime start, LocalTime end) {
}
