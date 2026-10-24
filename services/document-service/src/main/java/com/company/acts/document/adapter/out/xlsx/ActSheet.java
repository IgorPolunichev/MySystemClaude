package com.company.acts.document.adapter.out.xlsx;

import com.company.acts.document.application.model.ActPrintData;
import java.math.BigDecimal;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

/**
 * Лист «Акт» (раздел 7 SPEC.md): шапка, исполнитель и заказчик, техника, период, работы, исполнители и
 * трудозатраты, запчасти, подписи. Пустые поля не печатаются; реквизиты и время в пути — по галочкам акта.
 */
final class ActSheet implements SheetRenderer {

    /** Колонки: №, основная, 4 узкие. Ширина подобрана под A4 книжной ориентации. */
    private static final int[] WIDTHS = {5, 34, 18, 12, 12, 13};
    private static final int LAST = WIDTHS.length - 1;

    @Override
    public void render(XSSFWorkbook workbook, ExcelStyles styles, ActPrintData d) {
        Sheet sheet = workbook.createSheet("Акт");
        SheetWriter w = new SheetWriter(sheet, styles, WIDTHS);
        w.printSetup(d.number() != null ? "Акт № " + d.number() : "Черновик");

        w.put(w.row(24), 0, LAST, Text.title(d), styles.title);
        w.put(w.row(16), 0, LAST, "от " + Text.date(d.documentDate()), styles.subtitle);
        w.row(8);

        section(w, "Стороны");
        keyValue(w, "Исполнитель", d.executor().name(), true);
        keyValue(w, "Реквизиты исполнителя", Text.requisites(d.hideRequisites(), d.executor().inn(), d.executor().kpp(),
                d.executor().address(), d.executor().phone()), false);
        if (d.customer() != null) {
            keyValue(w, "Заказчик", d.customer().name(), true);
            keyValue(w, "Реквизиты заказчика", Text.requisites(d.hideRequisites(), d.customer().inn(),
                    d.customer().kpp(), d.customer().address(), null), false);
            keyValue(w, "Производственная площадка", d.customer().site(), false);
            keyValue(w, "Адрес площадки", d.customer().siteAddress(), false);
            keyValue(w, "Подразделение", d.customer().department(), false);
        }

        section(w, "Техника");
        if (d.equipment() != null) {
            ActPrintData.Equipment e = d.equipment();
            keyValue(w, "Тип", e.type(), false);
            keyValue(w, "Марка, модель", e.brand() + " " + e.model(), true);
            keyValue(w, "Серийный номер / VIN", e.serialNumber(), false);
            keyValue(w, "Гаражный номер", e.garageNumber(), false);
            keyValue(w, "Государственный номер", e.plateNumber(), false);
            keyValue(w, "Наработка", Text.meter(d), false);
        } else {
            keyValue(w, "Техника", "не указана", false);
        }

        section(w, "Период и место работ");
        keyValue(w, "Период работ", Text.period(d), false);
        keyValue(w, "Место проведения работ", d.location(), false);

        section(w, "Выполненные работы");
        keyValue(w, "Краткое описание", d.shortDescription(), true);
        if (d.detailedDescription() != null) {
            w.put(w.row(16), 0, LAST, "Подробное описание выполненных работ", styles.label);
            Row row = w.row(w.heightFor(d.detailedDescription(), 0, LAST, 30));
            w.put(row, 0, LAST, d.detailedDescription(), styles.text);
        }

        engineers(w, d);
        parts(w, d);
        Signatures.write(w, d, 0, 1, 3, LAST);
    }

    private static void keyValue(SheetWriter w, String label, String value, boolean bold) {
        if (value == null || value.isBlank()) {
            return;
        }
        Row row = w.row(w.heightForAll(16, new Object[] {value, 2, LAST}, new Object[] {label, 0, 1}));
        w.put(row, 0, 1, label, w.styles.label);
        w.put(row, 2, LAST, value, bold ? w.styles.valueBold : w.styles.value);
    }

    private static void section(SheetWriter w, String title) {
        w.row(6);
        w.put(w.row(18), 0, LAST, title, w.styles.section);
    }

    private static void engineers(SheetWriter w, ActPrintData d) {
        section(w, "Исполнители и трудозатраты");
        boolean travel = !d.hideTravelTime();
        Row head = w.row(28);
        w.put(head, 0, 0, "№", w.styles.tableHead);
        w.put(head, 1, 1, "ФИО", w.styles.tableHead);
        w.put(head, 2, 2, "Должность", w.styles.tableHead);
        w.put(head, 3, 3, "Дней", w.styles.tableHead);
        w.put(head, 4, 4, "Часы", w.styles.tableHead);
        w.put(head, 5, 5, travel ? "В пути, ч" : null, w.styles.tableHead);
        int n = 1;
        BigDecimal hours = BigDecimal.ZERO;
        BigDecimal travelHours = BigDecimal.ZERO;
        for (ActPrintData.Engineer e : d.engineers()) {
            Row row = w.row(w.heightForAll(16, new Object[] {e.fullName(), 1, 1}, new Object[] {e.position(), 2, 2}));
            w.put(row, 0, 0, String.valueOf(n++), w.styles.cellCenter);
            w.put(row, 1, 1, e.fullName(), w.styles.cell);
            w.put(row, 2, 2, e.position(), w.styles.cell);
            w.number(row, 3, 3, BigDecimal.valueOf(e.days()), w.styles.cellNumber);
            w.number(row, 4, 4, e.hours(), w.styles.cellNumber);
            w.number(row, 5, 5, travel ? e.travelHours() : null, w.styles.cellNumber);
            hours = hours.add(e.hours());
            travelHours = travelHours.add(e.travelHours());
        }
        Row total = w.row(16);
        w.put(total, 0, 3, "Итого", w.styles.cellTotal);
        w.number(total, 4, 4, hours, w.styles.cellTotalNumber);
        w.number(total, 5, 5, travel ? travelHours : null, w.styles.cellTotalNumber);
    }

    private static void parts(SheetWriter w, ActPrintData d) {
        section(w, "Использованные запчасти и материалы");
        if (d.parts().isEmpty()) {
            w.put(w.row(16), 0, LAST, "Не использовались", w.styles.cell);
            return;
        }
        Row head = w.row(28);
        w.put(head, 0, 0, "№", w.styles.tableHead);
        w.put(head, 1, 1, "Наименование", w.styles.tableHead);
        w.put(head, 2, 3, "Номер по каталогу", w.styles.tableHead);
        w.put(head, 4, 4, "Кол-во", w.styles.tableHead);
        w.put(head, 5, 5, "Ед. изм.", w.styles.tableHead);
        for (ActPrintData.Part p : d.parts()) {
            Row row = w.row(w.heightForAll(16, new Object[] {p.name(), 1, 1}, new Object[] {p.catalogNumber(), 2, 3}));
            w.put(row, 0, 0, String.valueOf(p.position()), w.styles.cellCenter);
            w.put(row, 1, 1, p.name(), w.styles.cell);
            w.put(row, 2, 3, p.catalogNumber(), w.styles.cell);
            w.number(row, 4, 4, p.quantity(), w.styles.cellNumber);
            w.put(row, 5, 5, p.unit(), w.styles.cellCenter);
        }
    }
}
