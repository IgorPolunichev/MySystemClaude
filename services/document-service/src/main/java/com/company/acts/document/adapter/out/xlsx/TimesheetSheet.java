package com.company.acts.document.adapter.out.xlsx;

import com.company.acts.document.application.model.ActPrintData;
import java.math.BigDecimal;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

/**
 * Лист «Табель»: по каждому дню и инженеру — интервал работы, перерывы и продуктивные часы (ADR 0009).
 * Выводится, если в акте есть записи времени. Строка заголовка таблицы повторяется на каждой печатной странице.
 */
final class TimesheetSheet implements SheetRenderer {

    @Override
    public void render(XSSFWorkbook workbook, ExcelStyles styles, ActPrintData d) {
        if (d.timeRows().isEmpty()) {
            return;
        }
        boolean travel = !d.hideTravelTime();
        int[] widths = travel ? new int[] {11, 24, 8, 10, 22, 9, 9} : new int[] {11, 26, 9, 11, 24, 10};
        int last = widths.length - 1;
        Sheet sheet = workbook.createSheet("Табель");
        SheetWriter w = new SheetWriter(sheet, styles, widths);
        w.printSetup(d.number() != null ? "Табель к акту № " + d.number() : "Табель");

        w.put(w.row(22), 0, last, "ТАБЕЛЬ РАБОЧЕГО ВРЕМЕНИ", styles.title);
        w.put(w.row(16), 0, last, (d.number() != null ? "к акту № " + d.number() : "к черновику акта")
                + " от " + Text.date(d.documentDate()), styles.subtitle);
        w.row(8);

        Row head = w.row(30);
        int headRow = head.getRowNum();
        w.put(head, 0, 0, "Дата", styles.tableHead);
        w.put(head, 1, 1, "Инженер", styles.tableHead);
        w.put(head, 2, 2, "Начало", styles.tableHead);
        w.put(head, 3, 3, "Окончание", styles.tableHead);
        w.put(head, 4, 4, "Перерывы", styles.tableHead);
        w.put(head, 5, 5, "Часы", styles.tableHead);
        if (travel) {
            w.put(head, 6, 6, "В пути, ч", styles.tableHead);
        }
        sheet.setRepeatingRows(new CellRangeAddress(headRow, headRow, -1, -1));

        BigDecimal hours = BigDecimal.ZERO;
        BigDecimal travelHours = BigDecimal.ZERO;
        for (ActPrintData.TimeRow t : d.timeRows()) {
            Row row = w.row(w.heightForAll(16, new Object[] {t.breaks(), 4, 4}, new Object[] {t.engineer(), 1, 1}));
            w.put(row, 0, 0, Text.date(t.date()), styles.cellCenter);
            w.put(row, 1, 1, t.engineer(), styles.cell);
            w.put(row, 2, 2, t.start(), styles.cellCenter);
            w.put(row, 3, 3, t.end(), styles.cellCenter);
            w.put(row, 4, 4, t.breaks(), styles.cell);
            w.number(row, 5, 5, t.hours(), styles.cellNumber);
            if (travel) {
                w.number(row, 6, 6, t.travelHours(), styles.cellNumber);
            }
            hours = hours.add(t.hours());
            if (t.travelHours() != null) {
                travelHours = travelHours.add(t.travelHours());
            }
        }
        Row total = w.row(16);
        w.put(total, 0, 4, "Итого продуктивных часов", styles.cellTotal);
        w.number(total, 5, 5, hours, styles.cellTotalNumber);
        if (travel) {
            w.number(total, 6, 6, travelHours, styles.cellTotalNumber);
        }
        w.row(8);
        w.put(w.row(28), 0, last, "Продуктивные часы рассчитаны как продолжительность работы за вычетом перерывов. "
                + "Окончание раньше начала означает окончание работы на следующие сутки.", styles.note);
    }
}
