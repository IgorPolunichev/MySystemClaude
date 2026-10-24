package com.company.acts.document.adapter.out.xlsx;

import com.company.acts.document.application.model.ActPrintData;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

/**
 * Один лист (или группа листов) печатной формы. Новые листы добавляются новыми реализациями, без правки
 * существующих (раздел 7 SPEC.md, «Шаблоны и расширяемость»).
 */
interface SheetRenderer {

    void render(XSSFWorkbook workbook, ExcelStyles styles, ActPrintData data);
}
