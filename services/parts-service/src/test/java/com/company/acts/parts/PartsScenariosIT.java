package com.company.acts.parts;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.company.acts.common.core.BusinessRuleException;
import com.company.acts.common.core.ConflictException;
import com.company.acts.common.core.PageQuery;
import com.company.acts.common.test.PostgresIntegrationTest;
import com.company.acts.parts.application.PartImportService;
import com.company.acts.parts.application.PartService;
import com.company.acts.parts.application.command.PartCommand;
import com.company.acts.parts.domain.Part;
import com.company.acts.parts.domain.Unit;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.test.context.support.WithMockUser;

@SpringBootTest
class PartsScenariosIT extends PostgresIntegrationTest {

    @Autowired
    PartService parts;
    @Autowired
    PartImportService importService;

    @Test
    @WithMockUser(roles = "ADMIN")
    void catalogRules() {
        assertThat(parts.units()).extracting(Unit::code).containsExactly("PCS", "L", "KG", "M", "SET");

        String number = "t-" + UUID.randomUUID().toString().substring(0, 8);
        Part filter = parts.create(new PartCommand(null, null, "Фильтр тестовый", number, "CAT", "pcs", null, null));
        assertThat(filter.catalogNumber()).isEqualTo(number.toUpperCase());
        assertThat(filter.defaultUnit()).isEqualTo("PCS");
        assertThat(filter.fluid()).isFalse();

        Part oil = parts.create(new PartCommand(null, null, "Масло тестовое", null, null, "L", null, null));
        assertThat(oil.fluid()).isTrue();

        assertThatThrownBy(() -> parts.create(new PartCommand(null, null, "Дубль", number, "cat", "PCS", null, null)))
                .isInstanceOf(ConflictException.class);
        assertThat(parts.create(new PartCommand(null, null, "Тот же номер, другой производитель", number, "Komatsu",
                "PCS", null, null)).id()).isNotEqualTo(filter.id());
        assertThatThrownBy(() -> parts.create(new PartCommand(null, null, "Неизвестная единица", null, null, "BOX",
                null, null))).isInstanceOf(BusinessRuleException.class);

        assertThat(parts.search(number.substring(2), PageQuery.of(0, 20, null)).totalElements()).isEqualTo(2);
    }

    @Test
    @WithMockUser(roles = "ENGINEER")
    void engineerCanAddButNotEdit() {
        Part part = parts.create(new PartCommand(null, null, "Добавил инженер", null, null, "PCS", null, null));
        assertThatThrownBy(() -> parts.update(part.id(), new PartCommand(null, part.version(), "Правка", null, null,
                "PCS", null, null))).isInstanceOf(AccessDeniedException.class);
    }

    @Test
    @WithMockUser(roles = "DEPARTMENT_HEAD")
    void importCreatesUpdatesAndReportsErrors() {
        String number = "IMP-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        String csv = "Наименование;Каталожный номер;Производитель;Ед. изм.;Жидкость\n"
                + "Фильтр из файла;" + number + ";CAT;шт;нет\n"
                + "Масло из файла " + number + ";;;л;да\n"
                + "Плохая строка;;;ящик;\n";

        var first = importService.importParts("parts.csv", csv.getBytes(StandardCharsets.UTF_8));
        assertThat(first.getCreated()).isEqualTo(2);
        assertThat(first.getErrors()).singleElement().satisfies(e -> assertThat(e.row()).isEqualTo(4));

        var second = importService.importParts("parts.csv",
                csv.replace("Фильтр из файла", "Фильтр из файла (обновлён)").getBytes(StandardCharsets.UTF_8));
        assertThat(second.getUpdated()).isEqualTo(2);
        assertThat(parts.search(number, PageQuery.of(0, 10, null)).items())
                .extracting(Part::name).contains("Фильтр из файла (обновлён)");
    }
}
