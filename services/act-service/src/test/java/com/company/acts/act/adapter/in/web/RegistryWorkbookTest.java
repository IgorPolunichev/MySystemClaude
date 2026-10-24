package com.company.acts.act.adapter.in.web;

import static org.assertj.core.api.Assertions.assertThat;

import com.company.acts.act.application.view.ActSummary;
import com.company.acts.act.domain.ActStatus;
import java.io.ByteArrayInputStream;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;

class RegistryWorkbookTest {

    @Test
    void writesOneRowPerAct() throws Exception {
        ActSummary act = new ActSummary(UUID.randomUUID(), "АВР-А-2026-000001", ActStatus.CLOSED, "АО «Тест»", "Цех",
                "CAT 777G", "гар. № 45", "ТО-2000", LocalDate.of(2026, 10, 5), LocalDate.of(2026, 10, 7),
                new BigDecimal("9.5"), "Петров И. С.", Instant.parse("2026-10-05T07:00:00Z"), null, true,
            "Площадка А", null, null);
        byte[] bytes = RegistryWorkbook.write(List.of(act, act), "поиск «ТО»");
        try (XSSFWorkbook wb = new XSSFWorkbook(new ByteArrayInputStream(bytes))) {
            var sheet = wb.getSheetAt(0);
            assertThat(sheet.getRow(2).getCell(0).getStringCellValue()).isEqualTo("Номер");
            assertThat(sheet.getRow(3).getCell(0).getStringCellValue()).isEqualTo("АВР-А-2026-000001");
            assertThat(sheet.getRow(3).getCell(1).getStringCellValue()).isEqualTo("Закрыт");
            assertThat(sheet.getRow(3).getCell(3).getStringCellValue()).isEqualTo("05.10.2026 — 07.10.2026");
            assertThat(sheet.getRow(3).getCell(11).getNumericCellValue()).isEqualTo(9.5);
            assertThat(sheet.getRow(4) != null).isTrue();
            assertThat(sheet.getRow(5) == null).isTrue();
        }
    }
}
