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
 * Листы «Техотчёт 1…N» (раздел 7 SPEC.md): на каждом — шапка акта; на первом — подробное описание и
 * рекомендации; фото сеткой 2 столбца × 3 ряда с подписями «Фото N. …»; на последнем — подписи.
 * Число листов = ⌈фото / 6⌉, но не меньше одного.
 */
final class TechReportSheets implements SheetRenderer {

    static final int PHOTOS_PER_SHEET = 6;
    private static final int COLUMN_CHARS = 47;
    private static final double PHOTO_ROW_POINTS = 215;
    private static final double CAPTION_ROW_POINTS = 30;
    private static final int PADDING_PX = 6;

    @Override
    public void render(XSSFWorkbook workbook, ExcelStyles styles, ActPrintData d) {
        List<ActPrintData.Photo> photos = d.photos();
        int sheets = Math.max(1, (int) Math.ceil(photos.size() / (double) PHOTOS_PER_SHEET));
        for (int s = 0; s < sheets; s++) {
            Sheet sheet = workbook.createSheet("Техотчёт " + (s + 1));
            SheetWriter w = new SheetWriter(sheet, styles, COLUMN_CHARS, COLUMN_CHARS);
            w.printSetup(d.number() != null ? "Техотчёт к акту № " + d.number() : "Техотчёт");
            header(w, d, s + 1, sheets);
            if (s == 0) {
                block(w, "Подробное описание выполненных работ", d.detailedDescription());
                block(w, "Рекомендации", d.recommendations());
            }
            List<ActPrintData.Photo> page = photos.subList(s * PHOTOS_PER_SHEET,
                    Math.min(photos.size(), (s + 1) * PHOTOS_PER_SHEET));
            if (page.isEmpty()) {
                w.row(8);
                w.put(w.row(18), 0, 1, "Фото не приложены", styles.value);
            } else {
                photoGrid(workbook, w, page);
            }
            if (s == sheets - 1) {
                Signatures.write(w, d, 0, 0, 1, 1);
            }
        }
    }

    private static void header(SheetWriter w, ActPrintData d, int sheet, int total) {
        String title = "ТЕХНИЧЕСКИЙ ОТЧЁТ" + (d.number() != null ? " к акту № " + d.number() : "")
                + " от " + Text.date(d.documentDate());
        w.put(w.row(22), 0, 1, title, w.styles.title);
        w.put(w.row(14), 0, 1, total > 1 ? "Лист " + sheet + " из " + total : null, w.styles.subtitle);
        line(w, "Заказчик", d.customer() == null ? null
                : d.customer().name() + (d.customer().department() != null ? ", " + d.customer().department() : ""));
        line(w, "Техника", Text.equipmentLine(d));
        line(w, "Период работ", Text.period(d));
        line(w, "Работы", d.shortDescription());
        w.row(6);
    }

    private static void line(SheetWriter w, String label, String value) {
        if (value == null || value.isBlank()) {
            return;
        }
        String text = label + ": " + value;
        Row row = w.row(w.heightFor(text, 0, 1, 16));
        w.put(row, 0, 1, text, w.styles.value);
    }

    private static void block(SheetWriter w, String title, String text) {
        if (text == null || text.isBlank()) {
            return;
        }
        w.put(w.row(18), 0, 1, title, w.styles.section);
        w.put(w.row(w.heightFor(text, 0, 1, 30)), 0, 1, text, w.styles.text);
        w.row(6);
    }

    /** Фото по два в ряд: ряд картинок и ряд подписей под ними. */
    private static void photoGrid(XSSFWorkbook workbook, SheetWriter w, List<ActPrintData.Photo> photos) {
        XSSFDrawing drawing = (XSSFDrawing) w.sheet.createDrawingPatriarch();
        for (int i = 0; i < photos.size(); i += 2) {
            Row pictures = w.row(PHOTO_ROW_POINTS);
            Row captions = w.row(CAPTION_ROW_POINTS);
            for (int col = 0; col < 2 && i + col < photos.size(); col++) {
                ActPrintData.Photo photo = photos.get(i + col);
                w.put(pictures, col, col, null, w.styles.caption);
                place(workbook, drawing, w.sheet, pictures.getRowNum(), col, photo);
                String caption = "Фото " + photo.number() + (photo.caption() != null ? ". " + photo.caption() : "");
                w.put(captions, col, col, caption, w.styles.caption);
                if (w.heightFor(caption, col, col, CAPTION_ROW_POINTS) > captions.getHeightInPoints()) {
                    captions.setHeightInPoints((float) w.heightFor(caption, col, col, CAPTION_ROW_POINTS));
                }
            }
        }
    }

    /** Картинка вписывается в ячейку с сохранением пропорций и выравнивается по центру. */
    private static void place(XSSFWorkbook workbook, XSSFDrawing drawing, Sheet sheet, int row, int col,
                              ActPrintData.Photo photo) {
        int pictureIndex = workbook.addPicture(photo.jpeg(), Workbook.PICTURE_TYPE_JPEG);
        double cellWidth = sheet.getColumnWidthInPixels(col);
        double cellHeight = Units.pointsToPixel(PHOTO_ROW_POINTS);
        double boxWidth = cellWidth - 2 * PADDING_PX;
        double boxHeight = cellHeight - 2 * PADDING_PX;
        double scale = Math.min(boxWidth / photo.width(), boxHeight / photo.height());
        double width = photo.width() * scale;
        double height = photo.height() * scale;
        int left = (int) Math.round((cellWidth - width) / 2);
        int top = (int) Math.round((cellHeight - height) / 2);
        XSSFClientAnchor anchor = new XSSFClientAnchor(
                left * Units.EMU_PER_PIXEL, top * Units.EMU_PER_PIXEL,
                (int) Math.round(left + width) * Units.EMU_PER_PIXEL, (int) Math.round(top + height) * Units.EMU_PER_PIXEL,
                col, row, col, row);
        anchor.setAnchorType(ClientAnchor.AnchorType.MOVE_AND_RESIZE);
        drawing.createPicture(anchor, pictureIndex);
    }
}
