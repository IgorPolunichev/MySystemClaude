package com.company.acts.document.application.port.out;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/** Данные акта из act-service (то, что нужно для печати). */
public interface ActSource {

    record ActData(UUID id, String number, String status, Instant createdAt, Instant closedAt, Party executor,
                   Org organization, SiteRef site,
                   Eq equipment, BigDecimal meterHours, BigDecimal meterKm, String location, LocalDate workStartDate,
                   LocalDate workEndDate, long calendarDays, String shortDescription, String detailedDescription,
                   String recommendations, String customerRepName, String customerRepPosition, boolean hideRequisites,
                   boolean hideTravelTime, List<Person> engineers, List<Time> timeEntries, List<PartLine> parts,
                   List<PhotoRef> photos) {
    }

    record Party(String name, String shortName, String inn, String kpp, String address, String phone) {
    }

    record SiteRef(String name, String address) {
    }

    record Org(String name, String shortName, String inn, String kpp, String legalAddress, String departmentName) {
    }

    record Eq(String typeName, String brand, String model, String serialNumber, String garageNumber,
              String plateNumber, String meterType) {
    }

    record Person(UUID engineerId, String fullName, String position, String role) {
    }

    record Break(String startTime, String endTime) {
    }

    record Time(UUID engineerId, LocalDate workDate, String startTime, String endTime, List<Break> breaks,
                int workMinutes, BigDecimal travelHours) {
    }

    record PartLine(int position, String name, String catalogNumber, BigDecimal quantity, String unitName) {
    }

    record PhotoRef(UUID mediaId, String caption, int position) {
    }

    ActData act(UUID actId);
}
