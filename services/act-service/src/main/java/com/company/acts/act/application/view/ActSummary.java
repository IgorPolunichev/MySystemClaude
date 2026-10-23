package com.company.acts.act.application.view;

import com.company.acts.act.domain.ActStatus;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/** Строка реестра актов. */
public record ActSummary(UUID id, String number, ActStatus status, String organizationName, String departmentName,
                         String equipmentTitle, String equipmentNumbers, String shortDescription,
                         LocalDate periodStart, LocalDate periodEnd, BigDecimal totalHours, String engineerNames,
                         Instant createdAt, Instant updatedAt) {
}
