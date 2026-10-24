package com.company.acts.document.adapter.out.xlsx;

import org.apache.poi.ss.usermodel.BorderStyle;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.HorizontalAlignment;
import org.apache.poi.ss.usermodel.IndexedColors;
import org.apache.poi.ss.usermodel.VerticalAlignment;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

/** Все стили документа в одном месте (раздел 7 SPEC.md): шрифт Arial 10, рамки таблиц, заголовки разделов. */
final class ExcelStyles {

    static final String FONT = "Arial";

    final CellStyle title;
    final CellStyle subtitle;
    final CellStyle section;
    final CellStyle label;
    final CellStyle value;
    final CellStyle valueBold;
    final CellStyle text;
    final CellStyle tableHead;
    final CellStyle cell;
    final CellStyle cellCenter;
    final CellStyle cellNumber;
    final CellStyle cellTotal;
    final CellStyle cellTotalNumber;
    final CellStyle signature;
    final CellStyle signatureLine;
    final CellStyle small;
    final CellStyle caption;

    ExcelStyles(XSSFWorkbook wb) {
        Font regular = font(wb, 10, false);
        Font bold = font(wb, 10, true);
        Font big = font(wb, 14, true);
        Font tiny = font(wb, 8, false);
        short number = wb.createDataFormat().getFormat("#,##0.##");

        title = style(wb, big, HorizontalAlignment.CENTER, false);
        subtitle = style(wb, regular, HorizontalAlignment.CENTER, false);
        section = style(wb, bold, HorizontalAlignment.LEFT, false);
        section.setFillForegroundColor(IndexedColors.GREY_25_PERCENT.getIndex());
        section.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        label = style(wb, regular, HorizontalAlignment.LEFT, false);
        label.setWrapText(true);
        value = style(wb, regular, HorizontalAlignment.LEFT, false);
        value.setWrapText(true);
        valueBold = style(wb, bold, HorizontalAlignment.LEFT, false);
        valueBold.setWrapText(true);
        text = style(wb, regular, HorizontalAlignment.LEFT, true);
        text.setWrapText(true);
        tableHead = style(wb, bold, HorizontalAlignment.CENTER, true);
        tableHead.setWrapText(true);
        tableHead.setFillForegroundColor(IndexedColors.GREY_25_PERCENT.getIndex());
        tableHead.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        cell = style(wb, regular, HorizontalAlignment.LEFT, true);
        cell.setWrapText(true);
        cellCenter = style(wb, regular, HorizontalAlignment.CENTER, true);
        cellNumber = style(wb, regular, HorizontalAlignment.RIGHT, true);
        cellNumber.setDataFormat(number);
        cellTotal = style(wb, bold, HorizontalAlignment.RIGHT, true);
        cellTotalNumber = style(wb, bold, HorizontalAlignment.RIGHT, true);
        cellTotalNumber.setDataFormat(number);
        signature = style(wb, regular, HorizontalAlignment.LEFT, false);
        signature.setWrapText(true);
        signatureLine = style(wb, regular, HorizontalAlignment.LEFT, false);
        signatureLine.setBorderBottom(BorderStyle.THIN);
        small = style(wb, tiny, HorizontalAlignment.CENTER, false);
        caption = style(wb, regular, HorizontalAlignment.CENTER, false);
        caption.setWrapText(true);
        caption.setVerticalAlignment(VerticalAlignment.TOP);
    }

    private static Font font(XSSFWorkbook wb, int size, boolean bold) {
        Font font = wb.createFont();
        font.setFontName(FONT);
        font.setFontHeightInPoints((short) size);
        font.setBold(bold);
        return font;
    }

    private static CellStyle style(XSSFWorkbook wb, Font font, HorizontalAlignment align, boolean border) {
        CellStyle style = wb.createCellStyle();
        style.setFont(font);
        style.setAlignment(align);
        style.setVerticalAlignment(VerticalAlignment.CENTER);
        if (border) {
            style.setBorderTop(BorderStyle.THIN);
            style.setBorderBottom(BorderStyle.THIN);
            style.setBorderLeft(BorderStyle.THIN);
            style.setBorderRight(BorderStyle.THIN);
        }
        return style;
    }
}
