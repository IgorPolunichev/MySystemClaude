package com.company.acts.document.application.model;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Всё, что нужно для печатной формы акта, — независимо от того, откуда пришли данные. Пустые поля
 * (null) не печатаются.
 */
public record ActPrintData(
        String number,
        String status,
        LocalDate documentDate,
        Company executor,
        Customer customer,
        Equipment equipment,
        BigDecimal meterHours,
        BigDecimal meterKm,
        String location,
        LocalDate workStart,
        LocalDate workEnd,
        long calendarDays,
        String shortDescription,
        String detailedDescription,
        String recommendations,
        String customerRepName,
        String customerRepPosition,
        boolean hideRequisites,
        boolean hideTravelTime,
        List<Engineer> engineers,
        List<TimeRow> timeRows,
        List<Part> parts,
        List<Photo> photos) {

    public record Company(String name, String inn, String kpp, String address, String phone) {
    }

    public record Customer(String name, String inn, String kpp, String address, String department) {
    }

    public record Equipment(String type, String brand, String model, String serialNumber, String garageNumber,
                            String plateNumber, String meterType) {
    }

    /** Исполнитель: дни и часы считаются из табеля. */
    public record Engineer(UUID id, String fullName, String position, boolean lead, long days, BigDecimal hours,
                           BigDecimal travelHours) {
    }

    /** Строка табеля: интервал, перерывы, продуктивные часы. */
    public record TimeRow(LocalDate date, String engineer, String start, String end, String breaks, BigDecimal hours,
                          BigDecimal travelHours) {
    }

    public record Part(int position, String name, String catalogNumber, BigDecimal quantity, String unit) {
    }

    /** Фото: подготовленный JPEG (уменьшенный для документа), его размер и подпись. */
    public record Photo(int number, String caption, byte[] jpeg, int width, int height) {
    }
}
