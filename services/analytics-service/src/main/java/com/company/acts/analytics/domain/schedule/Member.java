package com.company.acts.analytics.domain.schedule;

import java.util.UUID;

/**
 * Сотрудник в группе: бригада, порядок смен, индивидуальное смещение заезда (дней) и сменщик из другой бригады.
 */
public record Member(UUID employeeId, String name, Crew crew, Track track, int offsetDays, UUID partnerId) {

    public Member withOffset(int offset) {
        return new Member(employeeId, name, crew, track, offset, partnerId);
    }
}
