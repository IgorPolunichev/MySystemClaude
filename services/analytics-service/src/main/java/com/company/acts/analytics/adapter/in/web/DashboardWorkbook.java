package com.company.acts.analytics.adapter.in.web;

import com.company.acts.analytics.application.view.DashboardView;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.time.format.DateTimeFormatter;
import org.apache.poi.ss.usermodel.BorderStyle;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.IndexedColors;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

/** Таблица дашборда в Excel: инженеры (норма, факт, %, актов, машин) и отделы. */
final class DashboardWorkbook {

    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd.MM.yyyy");

    private DashboardWorkbook() {
    }

    static byte[] write(DashboardView view) {
        try (XSSFWorkbook wb = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Font bold = wb.createFont();
            bold.setBold(true);
            CellStyle head = wb.createCellStyle();
            head.setFont(bold);
            head.setFillForegroundColor(IndexedColors.GREY_25_PERCENT.getIndex());
            head.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            border(head);
            CellStyle cell = wb.createCellStyle();
            border(cell);
            CellStyle title = wb.createCellStyle();
            title.setFont(bold);

            Sheet sheet = wb.createSheet("Продуктивные часы");
            int r = 0;
            Row t = sheet.createRow(r++);
            text(t, 0, "Продуктивные часы за " + DATE.format(view.from()) + " — " + DATE.format(view.to()), title);
            r++;
            r = table(sheet, r, head, cell, new String[] {"Инженер", "Отдел", "Норма, ч", "Факт, ч", "%", "Актов", "Машин"},
                    new int[] {32, 30, 11, 11, 7, 8, 8});
            for (DashboardView.EngineerRow e : view.engineers()) {
                Row row = sheet.createRow(r++);
                text(row, 0, e.fullName(), cell);
                text(row, 1, e.departmentName(), cell);
                number(row, 2, e.normHours(), cell);
                number(row, 3, e.factHours(), cell);
                number(row, 4, e.normHours() == null ? null : BigDecimal.valueOf(e.percent()), cell);
                number(row, 5, BigDecimal.valueOf(e.acts()), cell);
                number(row, 6, BigDecimal.valueOf(e.machines()), cell);
            }
            if (!view.departments().isEmpty()) {
                r++;
                r = table(sheet, r, head, cell, new String[] {"Отдел", "", "Норма, ч", "Факт, ч", "%", "", ""}, null);
                for (DashboardView.DepartmentRow d : view.departments()) {
                    Row row = sheet.createRow(r++);
                    text(row, 0, d.name(), cell);
                    text(row, 1, d.normComputed() ? "норма — сумма норм инженеров" : null, cell);
                    number(row, 2, d.normHours(), cell);
                    number(row, 3, d.factHours(), cell);
                    number(row, 4, d.normHours() == null ? null : BigDecimal.valueOf(d.percent()), cell);
                }
            }
            wb.write(out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static int table(Sheet sheet, int r, CellStyle head, CellStyle cell, String[] headers, int[] widths) {
        Row row = sheet.createRow(r);
        for (int i = 0; i < headers.length; i++) {
            if (widths != null) {
                sheet.setColumnWidth(i, widths[i] * 256);
            }
            text(row, i, headers[i], head);
        }
        return r + 1;
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

    private static void number(Row row, int col, BigDecimal value, CellStyle style) {
        Cell c = row.createCell(col);
        c.setCellStyle(style);
        if (value != null) {
            c.setCellValue(value.doubleValue());
        }
    }
}
