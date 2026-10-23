package com.company.acts.common.importing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.company.acts.common.core.BusinessRuleException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.Test;

class TabularFileReaderTest {

    private static final List<ImportColumn> COLUMNS = List.of(
            ImportColumn.required("name", "", "Наименование", "Название"),
            ImportColumn.optional("number", "", "Каталожный номер", "Номер"),
            ImportColumn.optional("qty", "", "Количество"));

    @Test
    void readsSemicolonCsvInWindows1251WithQuotes() {
        String csv = "Название;Номер;Лишняя\r\n\"Фильтр; масляный\";1R-0750;x\r\n;;\r\nМасло 15W-40;;\r\n";
        byte[] bytes = csv.getBytes(Charset.forName("windows-1251"));

        List<ImportRow> rows = TabularFileReader.read("parts.csv", bytes, COLUMNS);

        assertThat(rows).hasSize(2);
        assertThat(rows.get(0).text("name")).isEqualTo("Фильтр; масляный");
        assertThat(rows.get(0).text("number")).isEqualTo("1R-0750");
        assertThat(rows.get(0).rowNumber()).isEqualTo(2);
        assertThat(rows.get(1).text("number")).isNull();
        assertThat(rows.get(1).rowNumber()).isEqualTo(4);
    }

    @Test
    void readsCommaCsvInUtf8WithBom() {
        String csv = "\uFEFFнаименование,количество\nМасло,\"45,5\"\n";
        List<ImportRow> rows = TabularFileReader.read("x.csv", csv.getBytes(StandardCharsets.UTF_8), COLUMNS);

        assertThat(rows).singleElement().satisfies(r -> {
            assertThat(r.text("name")).isEqualTo("Масло");
            assertThat(r.decimal("qty")).isEqualByComparingTo("45.5");
        });
    }

    @Test
    void readsTemplateItProduces() {
        byte[] xlsx = ImportTemplateWriter.write("Запчасти", COLUMNS, List.of("Фильтр", "1R-0750", "2"));

        List<ImportRow> rows = TabularFileReader.read("template.xlsx", xlsx, COLUMNS);

        assertThat(rows).singleElement().satisfies(r -> {
            assertThat(r.text("name")).isEqualTo("Фильтр");
            assertThat(r.integer("qty")).isEqualTo(2);
        });
    }

    @Test
    void failsWithoutRequiredColumn() {
        assertThatThrownBy(() -> TabularFileReader.read("x.csv", "Номер\n1\n".getBytes(StandardCharsets.UTF_8), COLUMNS))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("Наименование");
    }
}
