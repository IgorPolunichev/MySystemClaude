package com.company.acts.document.adapter.out.xlsx;

import java.math.BigDecimal;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.PrintSetup;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.util.CellRangeAddress;

/**
 * Построчная запись листа: курсор строки, объединение ячеек с одинаковым стилем (чтобы рамка была у всей
 * области), оценка высоты строки для длинного текста, настройки печати A4.
 */
final class SheetWriter {

    /** Примерная ширина одного символа Arial 10 в единицах ширины колонки — для оценки переноса строк. */
    private static final double LINE_HEIGHT_POINTS = 13.0;

    final Sheet sheet;
    final ExcelStyles styles;
    private final int[] widths;
    private int rowIndex;

    SheetWriter(Sheet sheet, ExcelStyles styles, int... columnWidthsInChars) {
        this.sheet = sheet;
        this.styles = styles;
        this.widths = columnWidthsInChars;
        for (int i = 0; i < widths.length; i++) {
            sheet.setColumnWidth(i, widths[i] * 256);
        }
    }

    int columns() {
        return widths.length;
    }

    int rowIndex() {
        return rowIndex;
    }

    Row row(double heightPoints) {
        Row row = sheet.createRow(rowIndex++);
        row.setHeightInPoints((float) heightPoints);
        return row;
    }

    void skip(int rows) {
        rowIndex += rows;
    }

    /** Текст в объединённых ячейках [from..to] текущей строки. */
    Cell put(Row row, int from, int to, String text, CellStyle style) {
        for (int c = from; c <= to; c++) {
            Cell cell = row.getCell(c) != null ? row.getCell(c) : row.createCell(c);
            cell.setCellStyle(style);
        }
        Cell cell = row.getCell(from);
        if (text != null) {
            cell.setCellValue(text);
        }
        if (to > from) {
            sheet.addMergedRegion(new CellRangeAddress(row.getRowNum(), row.getRowNum(), from, to));
        }
        return cell;
    }

    Cell number(Row row, int from, int to, BigDecimal value, CellStyle style) {
        Cell cell = put(row, from, to, null, style);
        if (value != null) {
            cell.setCellValue(value.doubleValue());
        }
        return cell;
    }

    /** Высота строки, в которую поместится текст в объединённых колонках [from..to]. */
    double heightFor(String text, int from, int to, double minPoints) {
        if (text == null || text.isBlank()) {
            return minPoints;
        }
        int chars = 0;
        for (int c = from; c <= to; c++) {
            chars += widths[c];
        }
        int perLine = Math.max(10, (int) (chars * 1.1));
        int lines = 0;
        for (String paragraph : text.split("\n", -1)) {
            lines += Math.max(1, (int) Math.ceil(paragraph.length() / (double) perLine));
        }
        return Math.max(minPoints, lines * LINE_HEIGHT_POINTS + 4);
    }

    /** A4, книжная, вписать по ширине в 1 страницу, поля 1,5 см, нумерация листов в колонтитуле. */
    void printSetup(String header) {
        PrintSetup print = sheet.getPrintSetup();
        print.setPaperSize(PrintSetup.A4_PAPERSIZE);
        print.setLandscape(false);
        sheet.setFitToPage(true);
        print.setFitWidth((short) 1);
        print.setFitHeight((short) 0);
        double cm = 1.5 / 2.54;
        sheet.setMargin(Sheet.LeftMargin, cm);
        sheet.setMargin(Sheet.RightMargin, cm);
        sheet.setMargin(Sheet.TopMargin, cm);
        sheet.setMargin(Sheet.BottomMargin, cm);
        sheet.setHorizontallyCenter(true);
        sheet.getHeader().setRight(header);
        sheet.getFooter().setCenter("Страница &P из &N");
    }
}
