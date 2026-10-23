package com.company.acts.act.application.view;

import java.time.Instant;

/** Запись журнала изменений акта. */
public record HistoryEntry(Instant at, String user, String action, String details) {
}
