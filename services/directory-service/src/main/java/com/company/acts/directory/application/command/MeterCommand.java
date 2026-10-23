package com.company.acts.directory.application.command;

import jakarta.validation.constraints.DecimalMin;
import java.math.BigDecimal;

/** Наработка из акта: моточасы и/или пробег. */
public record MeterCommand(@DecimalMin("0") BigDecimal hours, @DecimalMin("0") BigDecimal km) {
}
