package com.company.acts.document.adapter.out.xlsx;

import com.company.acts.document.application.model.ActPrintData;
import com.company.acts.document.application.port.out.DocumentRenderer;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.List;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Component;

/** Excel-файл акта: лист «Акт», «Табель» (если есть время) и листы техотчёта с фото. */
@Component
public class XlsxActRenderer implements DocumentRenderer {

    private final List<SheetRenderer> sheets = List.of(new ActSheet(), new TimesheetSheet(), new TechReportSheets());

    @Override
    public String format() {
        return "xlsx";
    }

    @Override
    public byte[] render(ActPrintData data) {
        try (XSSFWorkbook workbook = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            ExcelStyles styles = new ExcelStyles(workbook);
            for (SheetRenderer sheet : sheets) {
                sheet.render(workbook, styles, data);
            }
            workbook.setActiveSheet(0);
            workbook.write(out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
