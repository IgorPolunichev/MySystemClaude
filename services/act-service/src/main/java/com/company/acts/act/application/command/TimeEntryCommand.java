package com.company.acts.act.application.command;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalTime;
import java.util.List;

/**
 * Ячейка табеля «инженер × день»: интервал работы и перерывы. Продуктивные часы считает сервер:
 * окончание − начало − перерывы. Время — строкой «ЧЧ:ММ».
 */
public record TimeEntryCommand(
        LocalTime startTime,
        LocalTime endTime,
        @Valid @Size(max = 20, message = "Не больше 20 перерывов") List<BreakCommand> breaks,
        BigDecimal travelHours,
        @Size(max = 500) String comment) {

    public record BreakCommand(LocalTime startTime, LocalTime endTime) {
    }
}
