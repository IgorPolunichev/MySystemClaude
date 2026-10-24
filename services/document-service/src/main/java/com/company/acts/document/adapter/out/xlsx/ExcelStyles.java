package com.company.acts.document.adapter.out.xlsx;

import org.apache.poi.ss.usermodel.BorderStyle;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.HorizontalAlignment;
import org.apache.poi.ss.usermodel.VerticalAlignment;
import org.apache.poi.xssf.usermodel.XSSFCellStyle;
import org.apache.poi.xssf.usermodel.XSSFColor;
import org.apache.poi.xssf.usermodel.XSSFFont;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

/**
 * Оформление печатной формы. Палитра сдержанная, хорошо выглядит и в цвете, и в чёрно-белой печати:
 * тёмно-синие заголовки разделов с белым текстом, светло-серо-голубые подписи полей и шапки таблиц,
 * светлый янтарный — итоговые строки. Все ячейки таблиц и сетки «подпись | значение» — в тонких рамках.
 * Числа — в формате «Общий»: целые без запятой («4»), дробные — как есть («7,5»).
 */
final class ExcelStyles {

    static final String FONT = "Arial";
    private static final byte[] SECTION_FILL = {(byte) 31, (byte) 78, (byte) 121};
    private static final byte[] LABEL_FILL = {(byte) 234, (byte) 239, (byte) 245};
    private static final byte[] HEAD_FILL = {(byte) 214, (byte) 225, (byte) 236};
    private static final byte[] TOTAL_FILL = {(byte) 253, (byte) 242, (byte) 220};
    private static final byte[] BORDER = {(byte) 140, (byte) 155, (byte) 170};

    final XSSFCellStyle title;
    final XSSFCellStyle subtitle;
    final XSSFCellStyle section;
    final XSSFCellStyle label;
    final XSSFCellStyle value;
    final XSSFCellStyle valueBold;
    final XSSFCellStyle text;
    final XSSFCellStyle tableHead;
    final XSSFCellStyle cell;
    final XSSFCellStyle cellCenter;
    final XSSFCellStyle cellNumber;
    final XSSFCellStyle cellTotal;
    final XSSFCellStyle cellTotalNumber;
    final XSSFCellStyle signatureHead;
    final XSSFCellStyle signature;
    final XSSFCellStyle signatureLine;
    final XSSFCellStyle small;
    final XSSFCellStyle caption;
    final XSSFCellStyle photo;
    final XSSFCellStyle note;

    ExcelStyles(XSSFWorkbook wb) {
        XSSFFont regular = font(wb, 10, false, null);
        XSSFFont bold = font(wb, 10, true, null);
        XSSFFont big = font(wb, 14, true, SECTION_FILL);
        XSSFFont white = font(wb, 10, true, new byte[] {(byte) 255, (byte) 255, (byte) 255});
        XSSFFont tiny = font(wb, 8, false, new byte[] {(byte) 90, (byte) 100, (byte) 110});

        title = style(wb, big, HorizontalAlignment.CENTER, null, false);
        subtitle = style(wb, regular, HorizontalAlignment.CENTER, null, false);
        section = style(wb, white, HorizontalAlignment.LEFT, SECTION_FILL, true);
        label = style(wb, regular, HorizontalAlignment.LEFT, LABEL_FILL, true);
        label.setWrapText(true);
        value = style(wb, regular, HorizontalAlignment.LEFT, null, true);
        value.setWrapText(true);
        valueBold = style(wb, bold, HorizontalAlignment.LEFT, null, true);
        valueBold.setWrapText(true);
        text = style(wb, regular, HorizontalAlignment.LEFT, null, true);
        text.setWrapText(true);
        text.setVerticalAlignment(VerticalAlignment.TOP);
        tableHead = style(wb, bold, HorizontalAlignment.CENTER, HEAD_FILL, true);
        tableHead.setWrapText(true);
        cell = style(wb, regular, HorizontalAlignment.LEFT, null, true);
        cell.setWrapText(true);
        cellCenter = style(wb, regular, HorizontalAlignment.CENTER, null, true);
        cellNumber = style(wb, regular, HorizontalAlignment.RIGHT, null, true);
        cellTotal = style(wb, bold, HorizontalAlignment.RIGHT, TOTAL_FILL, true);
        cellTotalNumber = style(wb, bold, HorizontalAlignment.RIGHT, TOTAL_FILL, true);
        signatureHead = style(wb, bold, HorizontalAlignment.LEFT, HEAD_FILL, true);
        signature = style(wb, regular, HorizontalAlignment.LEFT, null, false);
        signature.setWrapText(true);
        signatureLine = style(wb, regular, HorizontalAlignment.LEFT, null, false);
        signatureLine.setBorderBottom(BorderStyle.THIN);
        signatureLine.setBottomBorderColor(color(BORDER));
        small = style(wb, tiny, HorizontalAlignment.CENTER, null, false);
        caption = style(wb, regular, HorizontalAlignment.CENTER, LABEL_FILL, true);
        caption.setWrapText(true);
        photo = style(wb, regular, HorizontalAlignment.CENTER, null, true);
        note = style(wb, tiny, HorizontalAlignment.LEFT, null, false);
        note.setWrapText(true);
    }

    private static XSSFColor color(byte[] rgb) {
        return new XSSFColor(rgb, null);
    }

    private static XSSFFont font(XSSFWorkbook wb, int size, boolean bold, byte[] rgb) {
        XSSFFont font = wb.createFont();
        font.setFontName(FONT);
        font.setFontHeightInPoints((short) size);
        font.setBold(bold);
        if (rgb != null) {
            font.setColor(color(rgb));
        }
        return font;
    }

    private static XSSFCellStyle style(XSSFWorkbook wb, XSSFFont font, HorizontalAlignment align, byte[] fill,
                                       boolean border) {
        XSSFCellStyle style = wb.createCellStyle();
        style.setFont(font);
        style.setAlignment(align);
        style.setVerticalAlignment(VerticalAlignment.CENTER);
        if (fill != null) {
            style.setFillForegroundColor(color(fill));
            style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        }
        if (border) {
            style.setBorderTop(BorderStyle.THIN);
            style.setBorderBottom(BorderStyle.THIN);
            style.setBorderLeft(BorderStyle.THIN);
            style.setBorderRight(BorderStyle.THIN);
            XSSFColor c = color(BORDER);
            style.setTopBorderColor(c);
            style.setBottomBorderColor(c);
            style.setLeftBorderColor(c);
            style.setRightBorderColor(c);
        }
        return style;
    }
}
