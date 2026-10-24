package com.company.acts.document.adapter.out.xlsx;

import com.company.acts.document.application.model.ActPrintData;
import java.util.List;
import org.apache.poi.ss.usermodel.ClientAnchor;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.util.Units;
import org.apache.poi.xssf.usermodel.XSSFClientAnchor;
import org.apache.poi.xssf.usermodel.XSSFDrawing;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

/**
 * Листы «Техотчёт 1…N» (раздел 7 SPEC.md). Сетка из четырёх колонок: шапка — таблица «подпись | значение»
 * (заказчик, площадка, техника по полям, наработка, период, работы); на первом листе — подробное описание и
 * рекомендации; фото по два в ряд (каждое занимает две колонки) с подписями «Фото N. …», 6 фото на лист;
 * на последнем листе — подписи сторон.
 */
final class TechReportSheets implements SheetRenderer {

    static final int PHOTOS_PER_SHEET = 6;
    private static final int[] WIDTHS = {16, 31, 16, 31};
    private static final int LAST = WIDTHS.length - 1;
    private static final double PHOTO_ROW_POINTS = 215;
    private static final double CAPTION_ROW_POINTS = 30;
    private static final int PADDING_PX = 6;

    @Override
    public void render(XSSFWorkbook workbook, ExcelStyles styles, ActPrintData d) {
        List<ActPrintData.Photo> photos = d.photos();
        int sheets = Math.max(1, (int) Math.ceil(photos.size() / (double) PHOTOS_PER_SHEET));
        for (int s = 0; s < sheets; s++) {
            Sheet sheet = workbook.createSheet("Техотчёт " + (s + 1));
            SheetWriter w = new SheetWriter(sheet, styles, WIDTHS);
            w.printSetup(d.number() != null ? "Техотчёт к акту № " + d.number() : "Техотчёт");
            header(w, d, s + 1, sheets);
            if (s == 0) {
                block(w, "Подробное описание выполненных работ", d.detailedDescription());
                block(w, "Рекомендации", d.recommendations());
            }
            List<ActPrintData.Photo> page = photos.subList(s * PHOTOS_PER_SHEET,
                    Math.min(photos.size(), (s + 1) * PHOTOS_PER_SHEET));
            w.row(6);
            w.put(w.row(18), 0, LAST, "Фотографии", styles.section);
            if (page.isEmpty()) {
                w.put(w.row(18), 0, LAST, "Фото не приложены", styles.cell);
            } else {
                photoGrid(workbook, w, page);
            }
            if (s == sheets - 1) {
                Signatures.write(w, d, 0, 1, 2, 3);
            }
        }
    }

    private static void header(SheetWriter w, ActPrintData d, int sheet, int total) {
        String title = "ТЕХНИЧЕСКИЙ ОТЧЁТ" + (d.number() != null ? " к акту № " + d.number() : "")
                + " от " + Text.date(d.documentDate());
        w.put(w.row(22), 0, LAST, title, w.styles.title);
        w.put(w.row(14), 0, LAST, total > 1 ? "Лист " + sheet + " из " + total : null, w.styles.subtitle);
        w.row(4);
        w.put(w.row(18), 0, LAST, "Сведения о работах", w.styles.section);
        if (d.customer() != null) {
            pair(w, "Заказчик", d.customer().name(), "Площадка", d.customer().site());
            pair(w, "Подразделение", d.customer().department(), "Адрес площадки", d.customer().siteAddress());
        }
        ActPrintData.Equipment e = d.equipment();
        if (e != null) {
            pair(w, "Техника", (e.type() != null ? e.type() + " " : "") + e.brand() + " " + e.model(),
                    "Наработка", Text.meter(d));
            pair(w, "Серийный номер", e.serialNumber(), "Гаражный номер", e.garageNumber());
            pair(w, "Госномер", e.plateNumber(), null, null);
        }
        pair(w, "Период работ", Text.period(d), "Место работ", d.location());
        if (d.shortDescription() != null) {
            Row row = w.row(w.heightFor(d.shortDescription(), 1, LAST, 16));
            w.put(row, 0, 0, "Работы", w.styles.label);
            w.put(row, 1, LAST, d.shortDescription(), w.styles.valueBold);
        }
    }

    /** Две пары «подпись | значение» в строке; пустая пара не печатается, одиночная занимает всю ширину. */
    private static void pair(SheetWriter w, String label1, String value1, String label2, String value2) {
        boolean first = value1 != null && !value1.isBlank();
        boolean second = value2 != null && !value2.isBlank();
        if (!first && !second) {
            return;
        }
        if (first && second) {
            Row row = w.row(w.heightForAll(16, new Object[] {value1, 1, 1}, new Object[] {value2, 3, 3},
                    new Object[] {label1, 0, 0}, new Object[] {label2, 2, 2}));
            w.put(row, 0, 0, label1, w.styles.label);
            w.put(row, 1, 1, value1, w.styles.value);
            w.put(row, 2, 2, label2, w.styles.label);
            w.put(row, 3, 3, value2, w.styles.value);
        } else {
            String label = first ? label1 : label2;
            String value = first ? value1 : value2;
            Row row = w.row(w.heightForAll(16, new Object[] {value, 1, LAST}, new Object[] {label, 0, 0}));
            w.put(row, 0, 0, label, w.styles.label);
            w.put(row, 1, LAST, value, w.styles.value);
        }
    }

    private static void block(SheetWriter w, String title, String text) {
        if (text == null || text.isBlank()) {
            return;
        }
        w.row(6);
        w.put(w.row(18), 0, LAST, title, w.styles.section);
        w.put(w.row(w.heightFor(text, 0, LAST, 30)), 0, LAST, text, w.styles.text);
    }

    /** Фото по два в ряд: ряд картинок (каждая в двух колонках) и ряд подписей под ними. */
    private static void photoGrid(XSSFWorkbook workbook, SheetWriter w, List<ActPrintData.Photo> photos) {
        XSSFDrawing drawing = (XSSFDrawing) w.sheet.createDrawingPatriarch();
        for (int i = 0; i < photos.size(); i += 2) {
            Row pictures = w.row(PHOTO_ROW_POINTS);
            Row captions = w.row(CAPTION_ROW_POINTS);
            for (int slot = 0; slot < 2; slot++) {
                int from = slot * 2;
                int to = from + 1;
                if (i + slot >= photos.size()) {
                    w.put(pictures, from, to, null, w.styles.photo);
                    w.put(captions, from, to, null, w.styles.caption);
                    continue;
                }
                ActPrintData.Photo photo = photos.get(i + slot);
                w.put(pictures, from, to, null, w.styles.photo);
                place(workbook, drawing, w.sheet, pictures.getRowNum(), from, photo);
                String caption = "Фото " + photo.number() + (photo.caption() != null ? ". " + photo.caption() : "");
                w.put(captions, from, to, caption, w.styles.caption);
                double needed = w.heightFor(caption, from, to, CAPTION_ROW_POINTS);
                if (needed > captions.getHeightInPoints()) {
                    captions.setHeightInPoints((float) needed);
                }
            }
        }
    }

    /** Картинка вписывается в область из двух колонок с сохранением пропорций и выравнивается по центру. */
    private static void place(XSSFWorkbook workbook, XSSFDrawing drawing, Sheet sheet, int row, int firstCol,
                              ActPrintData.Photo photo) {
        int pictureIndex = workbook.addPicture(photo.jpeg(), Workbook.PICTURE_TYPE_JPEG);
        double leftWidth = sheet.getColumnWidthInPixels(firstCol);
        double areaWidth = leftWidth + sheet.getColumnWidthInPixels(firstCol + 1);
        double areaHeight = Units.pointsToPixel(PHOTO_ROW_POINTS);
        double scale = Math.min((areaWidth - 2 * PADDING_PX) / photo.width(),
                (areaHeight - 2 * PADDING_PX) / photo.height());
        double width = photo.width() * scale;
        double height = photo.height() * scale;
        double x1 = (areaWidth - width) / 2;
        double x2 = x1 + width;
        int top = (int) Math.round((areaHeight - height) / 2);
        int bottom = (int) Math.round(top + height);
        int col1 = x1 < leftWidth ? firstCol : firstCol + 1;
        int dx1 = (int) Math.round(x1 < leftWidth ? x1 : x1 - leftWidth);
        int col2 = x2 <= leftWidth ? firstCol : firstCol + 1;
        int dx2 = (int) Math.round(x2 <= leftWidth ? x2 : x2 - leftWidth);
        XSSFClientAnchor anchor = new XSSFClientAnchor(dx1 * Units.EMU_PER_PIXEL, top * Units.EMU_PER_PIXEL,
                dx2 * Units.EMU_PER_PIXEL, bottom * Units.EMU_PER_PIXEL, col1, row, col2, row);
        anchor.setAnchorType(ClientAnchor.AnchorType.MOVE_AND_RESIZE);
        drawing.createPicture(anchor, pictureIndex);
    }
}
