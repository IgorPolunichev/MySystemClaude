package com.company.acts.document.adapter.out.xlsx;

import static org.assertj.core.api.Assertions.assertThat;

import com.company.acts.document.application.model.ActPrintData;
import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import javax.imageio.ImageIO;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFDrawing;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;

/**
 * Печатная форма на настоящем Apache POI: число листов и фото, скрытие реквизитов и времени в пути,
 * длинные тексты. Сгенерированные файлы складываются в target/xlsx-samples для просмотра глазами.
 */
class XlsxActRendererTest {

    private final XlsxActRenderer renderer = new XlsxActRenderer();

    @Test
    void sheetsAndPhotosPerSheet() throws Exception {
        for (int photos : new int[] {0, 1, 6, 7, 23}) {
            XSSFWorkbook wb = render(data(photos, true, true), "photos-" + photos);
            int reportSheets = Math.max(1, (int) Math.ceil(photos / 6.0));
            assertThat(wb.getNumberOfSheets()).as("листов при %d фото", photos).isEqualTo(2 + reportSheets);
            assertThat(wb.getSheetName(0)).isEqualTo("Акт");
            assertThat(wb.getSheetName(1)).isEqualTo("Табель");
            assertThat(wb.getSheetName(1 + reportSheets)).isEqualTo("Техотчёт " + reportSheets);
            int total = 0;
            for (int s = 2; s < wb.getNumberOfSheets(); s++) {
                int onSheet = pictures(wb.getSheetAt(s));
                assertThat(onSheet).isLessThanOrEqualTo(6);
                total += onSheet;
            }
            assertThat(total).isEqualTo(photos);
            assertThat(text(wb.getSheetAt(1 + reportSheets))).contains("Представитель исполнителя")
                    .contains("Площадка «Северный карьер»");
        }
    }

    @Test
    void requisitesAndTravelTimeFollowActFlags() throws Exception {
        XSSFWorkbook hidden = render(data(1, true, true), "hidden");
        assertThat(text(hidden.getSheetAt(0))).contains("АО «Тест»").doesNotContain("ИНН").doesNotContain("В пути");
        assertThat(text(hidden.getSheetAt(1))).doesNotContain("В пути");

        XSSFWorkbook shown = render(data(1, false, false), "shown");
        assertThat(text(shown.getSheetAt(0))).contains("ИНН 7707083893").contains("В пути, ч");
        assertThat(text(shown.getSheetAt(1))).contains("В пути, ч");
    }

    @Test
    void timesheetShowsIntervalsAndBreaks() throws Exception {
        XSSFWorkbook wb = render(data(0, true, true), "timesheet");
        String sheet = text(wb.getSheetAt(1));
        assertThat(sheet).contains("10:00").contains("13:00").contains("11:00–12:00").contains("Петров И. С.");
        assertThat(text(wb.getSheetAt(0))).contains("АКТ ВЫПОЛНЕННЫХ РАБОТ № АВР-2026-000047").contains("250,5 м/ч")
                .contains("Производственная площадка").contains("Представитель исполнителя");
        // часы — числом в формате «Общий»: целые без запятой
        var hours = wb.getSheetAt(1).getRow(4).getCell(5);
        assertThat(hours.getNumericCellValue()).isEqualTo(2.0);
        assertThat(hours.getCellStyle().getDataFormatString()).isEqualTo("General");
    }

    private XSSFWorkbook render(ActPrintData data, String sample) throws Exception {
        byte[] bytes = renderer.render(data);
        Path dir = Path.of("target", "xlsx-samples");
        Files.createDirectories(dir);
        Files.write(dir.resolve(sample + ".xlsx"), bytes);
        return new XSSFWorkbook(new ByteArrayInputStream(bytes));
    }

    private static int pictures(Sheet sheet) {
        XSSFDrawing drawing = ((XSSFSheet) sheet).getDrawingPatriarch();
        return drawing == null ? 0 : drawing.getShapes().size();
    }

    private static String text(Sheet sheet) {
        StringBuilder sb = new StringBuilder();
        for (Row row : sheet) {
            for (Cell cell : row) {
                if (cell.getCellType() == CellType.STRING) {
                    sb.append(cell.getStringCellValue()).append('\n');
                }
            }
        }
        return sb.toString();
    }

    static ActPrintData data(int photoCount, boolean hideRequisites, boolean hideTravel) throws Exception {
        UUID lead = UUID.randomUUID();
        String longText = "Выполнено ТО-2000 по регламенту завода-изготовителя. ".repeat(40);
        List<ActPrintData.Photo> photos = new ArrayList<>();
        byte[] jpeg = jpeg(1280, 960);
        for (int i = 1; i <= photoCount; i++) {
            photos.add(new ActPrintData.Photo(i, i % 3 == 0 ? null : "Узел № " + i + ": состояние после ремонта", jpeg,
                    i % 2 == 0 ? 960 : 1280, i % 2 == 0 ? 1280 : 960));
        }
        return new ActPrintData("АВР-2026-000047", "OPEN", LocalDate.of(2026, 10, 7),
                new ActPrintData.Company("ООО «Сервис»", "7707083893", "773601001", "г. Москва", "+7 495 000-00-00"),
                new ActPrintData.Customer("АО «Тест»", "7707083893", "773601001", "г. Воркута", "Участок 1",
                        "Площадка «Северный карьер»", "Воркута, карьер"),
                new ActPrintData.Equipment("Карьерный самосвал", "CAT", "777G", "CAT0777GTBY00123", "45", null, "HOURS"),
                new BigDecimal("1250.5"), null, "Бокс № 2", LocalDate.of(2026, 10, 5), LocalDate.of(2026, 10, 7), 3,
                "ТО-2000", longText, "Заменить ремни через 250 м/ч", "Сидоров А. В.", "Механик", hideRequisites,
                hideTravel,
                List.of(new ActPrintData.Engineer(lead, "Петров И. С.", "Сервисный инженер", true, 2,
                        new BigDecimal("9.50"), new BigDecimal("1.5"))),
                List.of(new ActPrintData.TimeRow(LocalDate.of(2026, 10, 5), "Петров И. С.", "10:00", "13:00",
                                "11:00–12:00", new BigDecimal("2.00"), new BigDecimal("1.5")),
                        new ActPrintData.TimeRow(LocalDate.of(2026, 10, 7), "Петров И. С.", "08:00", "17:00",
                                "12:00–13:00, 15:00–15:30", new BigDecimal("7.50"), null)),
                List.of(new ActPrintData.Part(1, "Фильтр масляный", "1R-0750", new BigDecimal("2"), "шт"),
                        new ActPrintData.Part(2, "Масло моторное 15W-40", null, new BigDecimal("45.5"), "л")),
                photos);
    }

    static byte[] jpeg(int width, int height) throws Exception {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        var g = image.createGraphics();
        g.setColor(new Color(120, 110, 95));
        g.fillRect(0, 0, width, height);
        g.dispose();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(image, "jpg", out);
        return out.toByteArray();
    }
}
