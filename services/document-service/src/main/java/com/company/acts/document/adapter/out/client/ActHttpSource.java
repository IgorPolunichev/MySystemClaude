package com.company.acts.document.adapter.out.client;

import com.company.acts.common.core.NotFoundException;
import com.company.acts.document.application.port.out.ActSource;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;

/** Карточка акта из act-service (те же права, что у пользователя, нажавшего «Скачать Excel»). */
@Component
class ActHttpSource implements ActSource {

    @JsonIgnoreProperties(ignoreUnknown = true)
    record ActDto(UUID id, String number, String status, Instant createdAt, Instant closedAt, OrgDto organization,
                  EqDto equipment, BigDecimal meterHours, BigDecimal meterKm, String location, LocalDate workStartDate,
                  LocalDate workEndDate, long calendarDays, String shortDescription, String detailedDescription,
                  String recommendations, String customerRepName, String customerRepPosition, boolean hideRequisites,
                  boolean hideTravelTime, List<PersonDto> engineers, List<TimeDto> timeEntries, List<PartDto> parts,
                  List<PhotoDto> photos) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record OrgDto(String name, String shortName, String inn, String kpp, String legalAddress, String departmentName) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record EqDto(String typeName, String brand, String model, String serialNumber, String garageNumber,
                 String plateNumber, String meterType) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record PersonDto(UUID engineerId, String fullName, String position, String role) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record BreakDto(String startTime, String endTime) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record TimeDto(UUID engineerId, LocalDate workDate, String startTime, String endTime, List<BreakDto> breaks,
                   int workMinutes, BigDecimal travelHours) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record PartDto(int position, String name, String catalogNumber, BigDecimal quantity, String unitName) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record PhotoDto(UUID mediaId, String caption, int position) {
    }

    private final RestClient client;

    ActHttpSource(@Value("${app.clients.act-url:http://localhost:8083}") String baseUrl) {
        this.client = BearerRelay.client(baseUrl);
    }

    @Override
    public ActData act(UUID actId) {
        ActDto a;
        try {
            a = client.get().uri("/api/v1/acts/{id}", actId).retrieve().body(ActDto.class);
        } catch (HttpClientErrorException.NotFound e) {
            throw NotFoundException.of("Акт", actId);
        }
        if (a == null) {
            throw NotFoundException.of("Акт", actId);
        }
        OrgDto o = a.organization();
        EqDto e = a.equipment();
        return new ActData(a.id(), a.number(), a.status(), a.createdAt(), a.closedAt(),
                o == null ? null : new Org(o.name(), o.shortName(), o.inn(), o.kpp(), o.legalAddress(), o.departmentName()),
                e == null ? null : new Eq(e.typeName(), e.brand(), e.model(), e.serialNumber(), e.garageNumber(),
                        e.plateNumber(), e.meterType()),
                a.meterHours(), a.meterKm(), a.location(), a.workStartDate(), a.workEndDate(), a.calendarDays(),
                a.shortDescription(), a.detailedDescription(), a.recommendations(), a.customerRepName(),
                a.customerRepPosition(), a.hideRequisites(), a.hideTravelTime(),
                list(a.engineers()).stream().map(p -> new Person(p.engineerId(), p.fullName(), p.position(), p.role())).toList(),
                list(a.timeEntries()).stream().map(t -> new Time(t.engineerId(), t.workDate(), t.startTime(), t.endTime(),
                        list(t.breaks()).stream().map(b -> new Break(b.startTime(), b.endTime())).toList(),
                        t.workMinutes(), t.travelHours())).toList(),
                list(a.parts()).stream().map(p -> new PartLine(p.position(), p.name(), p.catalogNumber(), p.quantity(),
                        p.unitName())).toList(),
                list(a.photos()).stream().map(p -> new PhotoRef(p.mediaId(), p.caption(), p.position())).toList());
    }

    private static <T> List<T> list(List<T> value) {
        return value == null ? List.of() : value;
    }
}
