package com.company.acts.act.application.command;

import jakarta.validation.constraints.Size;
import java.math.BigDecimal;

/** Ячейка табеля «инженер × день». */
public record TimeEntryCommand(BigDecimal hours, BigDecimal travelHours, @Size(max = 500) String comment) {
}
