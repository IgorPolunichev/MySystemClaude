package com.company.acts.common.importing;

import com.company.acts.common.core.BusinessRuleException;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.Charset;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;

/**
 * Читает первый лист .xlsx или CSV (разделитель «;» или «,», кодировка UTF-8 или Windows-1251 —
 * так сохраняет CSV русский Excel). Первая непустая строка — заголовки; колонки сопоставляются по
 * {@link ImportColumn}. Лишние колонки игнорируются, отсутствие обязательной — ошибка всего файла.
 */
public final class TabularFileReader {

    public static final int MAX_ROWS = 20_000;
    private static final Charset WINDOWS_1251 = Charset.forName("windows-1251");

    private TabularFileReader() {
    }

    public static List<ImportRow> read(String filename, byte[] content, List<ImportColumn> columns) {
        String name = filename == null ? "" : filename.toLowerCase(Locale.ROOT);
        List<List<String>> table;
        if (name.endsWith(".xlsx") || name.endsWith(".xls")) {
            table = readExcel(content);
        } else if (name.endsWith(".csv") || name.endsWith(".txt")) {
            table = readCsv(decode(content));
        } else {
            throw new BusinessRuleException("file", "Поддерживаются файлы Excel (.xlsx) и CSV");
        }
        return toRows(table, columns);
    }

    static List<ImportRow> toRows(List<List<String>> table, List<ImportColumn> columns) {
        int headerIndex = 0;
        while (headerIndex < table.size() && table.get(headerIndex).stream().allMatch(String::isBlank)) {
            headerIndex++;
        }
        if (headerIndex >= table.size()) {
            throw new BusinessRuleException("file", "Файл пустой");
        }
        List<String> headers = table.get(headerIndex);
        Map<String, Integer> positions = new HashMap<>();
        for (ImportColumn column : columns) {
            for (int i = 0; i < headers.size(); i++) {
                if (column.matches(headers.get(i))) {
                    positions.put(column.key(), i);
                    break;
                }
            }
            if (column.required() && !positions.containsKey(column.key())) {
                throw new BusinessRuleException("file", "В файле нет обязательной колонки «" + column.title() + "»");
            }
        }
        if (table.size() - headerIndex - 1 > MAX_ROWS) {
            throw new BusinessRuleException("file", "В файле больше " + MAX_ROWS + " строк — разделите его на части");
        }
        List<ImportRow> rows = new ArrayList<>();
        for (int r = headerIndex + 1; r < table.size(); r++) {
            List<String> cells = table.get(r);
            Map<String, String> values = new HashMap<>();
            positions.forEach((key, index) -> values.put(key, index < cells.size() ? cells.get(index) : null));
            ImportRow row = new ImportRow(r + 1, values);
            if (!row.isEmpty()) {
                rows.add(row);
            }
        }
        return rows;
    }

    private static List<List<String>> readExcel(byte[] content) {
        try (InputStream in = new ByteArrayInputStream(content); Workbook workbook = WorkbookFactory.create(in)) {
            Sheet sheet = workbook.getSheetAt(0);
            DataFormatter formatter = new DataFormatter(Locale.ROOT);
            List<List<String>> table = new ArrayList<>();
            for (int r = 0; r <= sheet.getLastRowNum(); r++) {
                Row row = sheet.getRow(r);
                List<String> cells = new ArrayList<>();
                if (row != null) {
                    for (int c = 0; c < row.getLastCellNum(); c++) {
                        Cell cell = row.getCell(c);
                        cells.add(cell == null ? "" : formatter.formatCellValue(cell).strip());
                    }
                }
                table.add(cells);
            }
            return table;
        } catch (IOException | RuntimeException e) {
            throw new BusinessRuleException("file", "Не удалось прочитать файл Excel: " + e.getMessage());
        }
    }

    static String decode(byte[] content) {
        int offset = content.length >= 3 && (content[0] & 0xFF) == 0xEF && (content[1] & 0xFF) == 0xBB
                && (content[2] & 0xFF) == 0xBF ? 3 : 0;
        ByteBuffer buffer = ByteBuffer.wrap(content, offset, content.length - offset);
        try {
            return StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(buffer).toString();
        } catch (CharacterCodingException e) {
            return new String(content, offset, content.length - offset, WINDOWS_1251);
        }
    }

    /** Разбор CSV по RFC 4180 с автоопределением разделителя по первой строке. */
    static List<List<String>> readCsv(String text) {
        int firstLineEnd = text.indexOf('\n');
        String firstLine = firstLineEnd < 0 ? text : text.substring(0, firstLineEnd);
        char delimiter = count(firstLine, ';') >= count(firstLine, ',') ? ';' : ',';

        List<List<String>> table = new ArrayList<>();
        List<String> row = new ArrayList<>();
        StringBuilder cell = new StringBuilder();
        boolean quoted = false;
        for (int i = 0; i < text.length(); i++) {
            char ch = text.charAt(i);
            if (quoted) {
                if (ch == '"') {
                    if (i + 1 < text.length() && text.charAt(i + 1) == '"') {
                        cell.append('"');
                        i++;
                    } else {
                        quoted = false;
                    }
                } else {
                    cell.append(ch);
                }
            } else if (ch == '"') {
                quoted = true;
            } else if (ch == delimiter) {
                row.add(cell.toString().strip());
                cell.setLength(0);
            } else if (ch == '\n') {
                row.add(cell.toString().strip());
                cell.setLength(0);
                table.add(row);
                row = new ArrayList<>();
            } else if (ch != '\r') {
                cell.append(ch);
            }
        }
        if (cell.length() > 0 || !row.isEmpty()) {
            row.add(cell.toString().strip());
            table.add(row);
        }
        return table;
    }

    private static int count(String s, char ch) {
        int n = 0;
        for (int i = 0; i < s.length(); i++) {
            if (s.charAt(i) == ch) {
                n++;
            }
        }
        return n;
    }
}
