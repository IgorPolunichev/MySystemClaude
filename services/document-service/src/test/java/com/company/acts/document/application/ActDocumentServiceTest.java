package com.company.acts.document.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.company.acts.document.application.model.ActPrintData;
import com.company.acts.document.application.port.out.ActSource;
import com.company.acts.document.application.port.out.DocumentRenderer;
import com.company.acts.document.application.port.out.ImageScaler;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** Сборка данных для печати: часы из минут табеля, дни, перерывы, порядок фото. */
class ActDocumentServiceTest {

    @Test
    void buildsPrintDataFromAct() {
        UUID lead = UUID.randomUUID();
        UUID member = UUID.randomUUID();
        UUID photoA = UUID.randomUUID();
        UUID photoB = UUID.randomUUID();
        ActSource.ActData act = new ActSource.ActData(UUID.randomUUID(), "АВР-2026-000001", "CLOSED",
                Instant.parse("2026-10-05T07:00:00Z"), Instant.parse("2026-10-07T20:30:00Z"),
                new ActSource.Org("АО «Тест»", "АО «Т»", "7707083893", null, null, "Цех"),
                new ActSource.Eq("Самосвал", "CAT", "777G", "SN1", "45", null, "HOURS"),
                new BigDecimal("100"), null, null, LocalDate.of(2026, 10, 5), LocalDate.of(2026, 10, 7), 3, "ТО", null,
                null, null, null, true, true,
                List.of(new ActSource.Person(lead, "Петров И. С.", "Инженер", "LEAD"),
                        new ActSource.Person(member, "Кузнецов С. А.", "Инженер", "MEMBER")),
                List.of(new ActSource.Time(lead, LocalDate.of(2026, 10, 5), "10:00", "13:00",
                                List.of(new ActSource.Break("11:00", "12:00")), 120, new BigDecimal("1.5")),
                        new ActSource.Time(lead, LocalDate.of(2026, 10, 7), "09:00", "11:20", List.of(), 140, null),
                        new ActSource.Time(member, LocalDate.of(2026, 10, 5), "09:00", "10:00", List.of(), 60, null)),
                List.of(), List.of(new ActSource.PhotoRef(photoB, "второе", 2), new ActSource.PhotoRef(photoA, "первое", 1)));

        DocumentRenderer noop = new DocumentRenderer() {
            public String format() {
                return "xlsx";
            }

            public byte[] render(ActPrintData data) {
                return new byte[0];
            }
        };
        ImageScaler scaler = (image, max) -> new ImageScaler.Scaled(image, 4, 3);
        ActDocumentService service = new ActDocumentService(id -> act, id -> id.toString().getBytes(), scaler,
                List.of(noop), "ООО «Сервис»", "", "", "", "", "Europe/Moscow");

        ActPrintData d = service.toPrintData(act);

        assertThat(d.documentDate()).isEqualTo(LocalDate.of(2026, 10, 7));
        assertThat(d.executor().inn()).isNull();
        ActPrintData.Engineer petrov = d.engineers().get(0);
        assertThat(petrov.lead()).isTrue();
        assertThat(petrov.days()).isEqualTo(2);
        assertThat(petrov.hours()).isEqualByComparingTo("4.33"); // 120 + 140 минут = 4 ч 20 мин
        assertThat(petrov.travelHours()).isEqualByComparingTo("1.5");
        assertThat(d.timeRows()).hasSize(3);
        assertThat(d.timeRows().get(0).date()).isEqualTo(LocalDate.of(2026, 10, 5));
        assertThat(d.timeRows()).anySatisfy(r -> assertThat(r.breaks()).isEqualTo("11:00–12:00"));
        assertThat(d.photos()).extracting(ActPrintData.Photo::caption).containsExactly("первое", "второе");
        assertThat(d.photos()).extracting(ActPrintData.Photo::number).containsExactly(1, 2);
    }
}
