package com.company.acts.document.application;

import com.company.acts.common.core.Strings;
import com.company.acts.document.application.model.ActPrintData;
import com.company.acts.document.application.port.out.ActSource;
import com.company.acts.document.application.port.out.DocumentRenderer;
import com.company.acts.document.application.port.out.ImageScaler;
import com.company.acts.document.application.port.out.PhotoSource;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * Печатная форма акта (раздел 7 SPEC.md): собирает данные акта, реквизиты исполнителя из настроек и фото,
 * уменьшенные для документа, и передаёт их рендереру нужного формата.
 */
@Service
public class ActDocumentService {

    /** Сторона фото в документе: достаточно для печати A4, файл остаётся лёгким. */
    static final int PHOTO_MAX_SIDE = 1280;

    /** Готовый файл. */
    public record Document(String filename, String contentType, byte[] content) {
    }

    private final ActSource acts;
    private final PhotoSource photos;
    private final ImageScaler scaler;
    private final Map<String, DocumentRenderer> renderers;
    private final ActPrintData.Company executor;
    private final ZoneId zone;

    public ActDocumentService(ActSource acts, PhotoSource photos, ImageScaler scaler, List<DocumentRenderer> renderers,
                              @Value("${app.company.name:ООО «Сервисная компания»}") String name,
                              @Value("${app.company.inn:}") String inn,
                              @Value("${app.company.kpp:}") String kpp,
                              @Value("${app.company.address:}") String address,
                              @Value("${app.company.phone:}") String phone,
                              @Value("${app.zone:Europe/Moscow}") String zone) {
        this.acts = acts;
        this.photos = photos;
        this.scaler = scaler;
        this.renderers = renderers.stream().collect(Collectors.toMap(DocumentRenderer::format, Function.identity()));
        this.executor = new ActPrintData.Company(name, Strings.trimToNull(inn), Strings.trimToNull(kpp),
                Strings.trimToNull(address), Strings.trimToNull(phone));
        this.zone = ZoneId.of(zone);
    }

    public Document actWorkbook(UUID actId) {
        ActSource.ActData act = acts.act(actId);
        ActPrintData data = toPrintData(act);
        byte[] content = renderers.get("xlsx").render(data);
        String equipment = act.equipment() == null ? "" : "_" + safe(act.equipment().brand() + act.equipment().model())
                + (act.equipment().garageNumber() != null ? "_гар" + safe(act.equipment().garageNumber()) : "");
        String filename = (act.number() != null ? act.number() : "Черновик") + equipment + ".xlsx";
        return new Document(filename, "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", content);
    }

    ActPrintData toPrintData(ActSource.ActData a) {
        Map<UUID, String> names = a.engineers().stream()
                .collect(Collectors.toMap(ActSource.Person::engineerId, ActSource.Person::fullName, (x, y) -> x));
        List<ActPrintData.Engineer> engineers = a.engineers().stream().map(p -> {
            List<ActSource.Time> own = a.timeEntries().stream().filter(t -> t.engineerId().equals(p.engineerId())).toList();
            long days = own.stream().map(ActSource.Time::workDate).distinct().count();
            BigDecimal hours = hours(own.stream().mapToLong(ActSource.Time::workMinutes).sum());
            BigDecimal travel = own.stream().map(ActSource.Time::travelHours).filter(Objects::nonNull)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            return new ActPrintData.Engineer(p.engineerId(), p.fullName(), p.position(), "LEAD".equals(p.role()), days,
                    hours, travel);
        }).toList();
        List<ActPrintData.TimeRow> rows = a.timeEntries().stream()
                .sorted(Comparator.comparing(ActSource.Time::workDate)
                        .thenComparing(t -> names.getOrDefault(t.engineerId(), "")))
                .map(t -> new ActPrintData.TimeRow(t.workDate(), names.getOrDefault(t.engineerId(), "—"), t.startTime(),
                        t.endTime(), t.breaks() == null || t.breaks().isEmpty() ? null : t.breaks().stream()
                        .map(b -> b.startTime() + "–" + b.endTime()).collect(Collectors.joining(", ")),
                        hours(t.workMinutes()), t.travelHours()))
                .toList();
        List<ActPrintData.Part> parts = a.parts().stream()
                .map(p -> new ActPrintData.Part(p.position(), p.name(), p.catalogNumber(), p.quantity(), p.unitName()))
                .toList();
        List<ActSource.PhotoRef> refs = a.photos().stream().sorted(Comparator.comparingInt(ActSource.PhotoRef::position))
                .toList();
        List<ActPrintData.Photo> printPhotos = new java.util.ArrayList<>();
        for (int i = 0; i < refs.size(); i++) {
            ImageScaler.Scaled scaled = scaler.fit(photos.original(refs.get(i).mediaId()), PHOTO_MAX_SIDE);
            printPhotos.add(new ActPrintData.Photo(i + 1, refs.get(i).caption(), scaled.jpeg(), scaled.width(),
                    scaled.height()));
        }
        ActSource.Org o = a.organization();
        ActPrintData.Customer customer = o == null ? null : new ActPrintData.Customer(
                o.name(), o.inn(), o.kpp(), o.legalAddress(), o.departmentName(),
                a.site() == null ? null : a.site().name(), a.site() == null ? null : a.site().address());
        ActSource.Party x = a.executor();
        ActPrintData.Company actExecutor = x == null ? executor
                : new ActPrintData.Company(x.name(), x.inn(), x.kpp(), x.address(), x.phone());
        ActSource.Eq e = a.equipment();
        ActPrintData.Equipment equipment = e == null ? null : new ActPrintData.Equipment(e.typeName(), e.brand(),
                e.model(), e.serialNumber(), e.garageNumber(), e.plateNumber(), e.meterType());
        LocalDate documentDate = (a.closedAt() != null ? a.closedAt() : java.time.Instant.now()).atZone(zone).toLocalDate();
        return new ActPrintData(a.number(), a.status(), documentDate, actExecutor, customer, equipment, a.meterHours(),
                a.meterKm(), a.location(), a.workStartDate(), a.workEndDate(), a.calendarDays(), a.shortDescription(),
                a.detailedDescription(), a.recommendations(), a.customerRepName(), a.customerRepPosition(),
                a.hideRequisites(), a.hideTravelTime(), engineers, rows, parts, printPhotos);
    }

    private static BigDecimal hours(long minutes) {
        return BigDecimal.valueOf(minutes).divide(BigDecimal.valueOf(60), 2, RoundingMode.HALF_UP);
    }

    private static String safe(String value) {
        return value.replaceAll("[^\\p{L}\\p{Nd}-]", "");
    }
}
