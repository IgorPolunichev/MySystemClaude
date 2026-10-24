package com.company.acts.act.adapter.in.web;

import com.company.acts.act.application.view.ActSummary;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import org.apache.poi.ss.usermodel.BorderStyle;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.IndexedColors;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.VerticalAlignment;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

/** Реестр актов одной таблицей (раздел 8 SPEC.md, «Экспорт реестра»): текущая выборка с фильтрами, без фото. */
final class RegistryWorkbook {

    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd.MM.yyyy");
    private static final ZoneId ZONE = ZoneId.of("Europe/Moscow");
    private static final Map<String, String> STATUS = Map.of("DRAFT", "Черновик", "OPEN", "Открыт",
            "CLOSED", "Закрыт", "CANCELLED", "Аннулирован");
    private static final String[] HEADERS = {"Номер", "Статус", "Создан", "Период работ", "Заказчик", "Площадка",
        "Подразделение", "Техника", "Номера техники", "Работы", "Инженеры", "Часы", "Скан"};
    private static final int[] WIDTHS = {20, 12, 11, 23, 26, 20, 20, 20, 22, 34, 30, 8, 7};

    private RegistryWorkbook() {
    }

    static byte[] write(List<ActSummary> acts, String filterDescription) {
        try (XSSFWorkbook wb = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet sheet = wb.createSheet("Реестр актов");
            Font bold = wb.createFont();
            bold.setBold(true);
            CellStyle head = wb.createCellStyle();
            head.setFont(bold);
            head.setWrapText(true);
            head.setVerticalAlignment(VerticalAlignment.CENTER);
            head.setFillForegroundColor(IndexedColors.GREY_25_PERCENT.getIndex());
            head.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            border(head);
            CellStyle cell = wb.createCellStyle();
            cell.setWrapText(true);
            cell.setVerticalAlignment(VerticalAlignment.TOP);
            border(cell);

            Row title = sheet.createRow(0);
            Cell t = title.createCell(0);
            t.setCellValue("Реестр актов выполненных работ на " + DATE.format(LocalDate.now(ZONE))
                    + (filterDescription.isBlank() ? "" : " · " + filterDescription));
            t.setCellStyle(styleBold(wb, bold));
            sheet.addMergedRegion(new CellRangeAddress(0, 0, 0, HEADERS.length - 1));

            Row header = sheet.createRow(2);
            for (int i = 0; i < HEADERS.length; i++) {
                sheet.setColumnWidth(i, WIDTHS[i] * 256);
                Cell c = header.createCell(i);
                c.setCellValue(HEADERS[i]);
                c.setCellStyle(head);
            }
            int r = 3;
            for (ActSummary a : acts) {
                Row row = sheet.createRow(r++);
                text(row, 0, a.number() != null ? a.number() : "черновик", cell);
                text(row, 1, STATUS.getOrDefault(a.status().name(), a.status().name()), cell);
                text(row, 2, a.createdAt() == null ? null : DATE.format(a.createdAt().atZone(ZONE)), cell);
                text(row, 3, period(a.periodStart(), a.periodEnd()), cell);
                text(row, 4, a.organizationName(), cell);
                text(row, 5, a.siteName(), cell);
                text(row, 6, a.departmentName(), cell);
                text(row, 7, a.equipmentTitle(), cell);
                text(row, 8, a.equipmentNumbers(), cell);
                text(row, 9, a.shortDescription(), cell);
                text(row, 10, a.engineerNames(), cell);
                Cell hours = row.createCell(11);
                hours.setCellStyle(cell);
                if (a.totalHours() != null) {
                    hours.setCellValue(a.totalHours().doubleValue());
                }
                text(row, 12, a.hasSignedScan() ? "да" : "", cell);
            }
            sheet.createFreezePane(1, 3);
            sheet.setAutoFilter(new CellRangeAddress(2, Math.max(2, r - 1), 0, HEADERS.length - 1));
            sheet.getPrintSetup().setLandscape(true);
            sheet.setFitToPage(true);
            sheet.getPrintSetup().setFitWidth((short) 1);
            sheet.getPrintSetup().setFitHeight((short) 0);
            sheet.setRepeatingRows(new CellRangeAddress(2, 2, -1, -1));
            wb.write(out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static CellStyle styleBold(XSSFWorkbook wb, Font bold) {
        CellStyle s = wb.createCellStyle();
        s.setFont(bold);
        return s;
    }

    private static void border(CellStyle s) {
        s.setBorderTop(BorderStyle.THIN);
        s.setBorderBottom(BorderStyle.THIN);
        s.setBorderLeft(BorderStyle.THIN);
        s.setBorderRight(BorderStyle.THIN);
    }

    private static void text(Row row, int col, String value, CellStyle style) {
        Cell c = row.createCell(col);
        c.setCellStyle(style);
        if (value != null) {
            c.setCellValue(value);
        }
    }

    private static String period(LocalDate start, LocalDate end) {
        if (start == null && end == null) {
            return null;
        }
        if (start == null || end == null || start.equals(end)) {
            return DATE.format(start != null ? start : end);
        }
        return DATE.format(start) + " — " + DATE.format(end);
    }
}
