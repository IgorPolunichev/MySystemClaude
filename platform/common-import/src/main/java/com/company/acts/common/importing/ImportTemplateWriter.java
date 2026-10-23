package com.company.acts.common.importing;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.List;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.IndexedColors;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

/** Шаблон импорта .xlsx: строка заголовков (обязательные — жирным), строка-пример и лист с пояснениями. */
public final class ImportTemplateWriter {

    private ImportTemplateWriter() {
    }

    public static byte[] write(String sheetName, List<ImportColumn> columns, List<String> example) {
        try (XSSFWorkbook workbook = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet sheet = workbook.createSheet(sheetName);
            Font bold = workbook.createFont();
            bold.setBold(true);
            CellStyle requiredStyle = workbook.createCellStyle();
            requiredStyle.setFont(bold);
            requiredStyle.setFillForegroundColor(IndexedColors.LIGHT_YELLOW.getIndex());
            requiredStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            CellStyle optionalStyle = workbook.createCellStyle();
            optionalStyle.setFillForegroundColor(IndexedColors.GREY_25_PERCENT.getIndex());
            optionalStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);

            Row header = sheet.createRow(0);
            Row sample = sheet.createRow(1);
            for (int i = 0; i < columns.size(); i++) {
                ImportColumn column = columns.get(i);
                var cell = header.createCell(i);
                cell.setCellValue(column.title());
                cell.setCellStyle(column.required() ? requiredStyle : optionalStyle);
                if (example != null && i < example.size() && example.get(i) != null) {
                    sample.createCell(i).setCellValue(example.get(i));
                }
                sheet.setColumnWidth(i, Math.max(14, column.title().length() + 4) * 256);
            }
            sheet.createFreezePane(0, 1);

            Sheet help = workbook.createSheet("Пояснения");
            Row helpHeader = help.createRow(0);
            helpHeader.createCell(0).setCellValue("Колонка");
            helpHeader.createCell(1).setCellValue("Обязательная");
            helpHeader.createCell(2).setCellValue("Что указывать");
            for (int i = 0; i < columns.size(); i++) {
                ImportColumn column = columns.get(i);
                Row row = help.createRow(i + 1);
                row.createCell(0).setCellValue(column.title());
                row.createCell(1).setCellValue(column.required() ? "да" : "нет");
                row.createCell(2).setCellValue(column.hint());
            }
            help.setColumnWidth(0, 28 * 256);
            help.setColumnWidth(1, 14 * 256);
            help.setColumnWidth(2, 80 * 256);

            workbook.write(out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
