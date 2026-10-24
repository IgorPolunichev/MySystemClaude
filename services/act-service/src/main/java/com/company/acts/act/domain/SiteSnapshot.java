package com.company.acts.act.domain;

import java.util.UUID;

/** Производственная площадка заказчика на момент работ. */
public record SiteSnapshot(UUID id, String name, String code, String address) {
}
