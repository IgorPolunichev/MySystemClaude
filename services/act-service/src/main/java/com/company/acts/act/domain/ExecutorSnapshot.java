package com.company.acts.act.domain;

import java.util.UUID;

/** Исполнитель на момент работ (снимок реквизитов для печати). */
public record ExecutorSnapshot(UUID id, String name, String shortName, String inn, String kpp, String address,
                               String phone) {
}
