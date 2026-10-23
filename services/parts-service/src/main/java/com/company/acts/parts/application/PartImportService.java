package com.company.acts.parts.application;

import com.company.acts.common.core.BusinessRuleException;
import com.company.acts.common.core.ConflictException;
import com.company.acts.common.core.NotFoundException;
import com.company.acts.common.core.Strings;
import com.company.acts.common.importing.ImportColumn;
import com.company.acts.common.importing.ImportResult;
import com.company.acts.common.importing.ImportRow;
import com.company.acts.common.importing.ImportRowException;
import com.company.acts.common.importing.ImportTemplateWriter;
import com.company.acts.common.importing.TabularFileReader;
import com.company.acts.parts.application.command.PartCommand;
import com.company.acts.parts.application.port.out.PartRepository;
import com.company.acts.parts.application.port.out.UnitRepository;
import com.company.acts.parts.domain.Part;
import com.company.acts.parts.domain.Unit;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;

/** Импорт каталога из Excel/CSV: строка с ошибкой пропускается, существующие позиции обновляются. */
@Service
public class PartImportService {

    public static final List<ImportColumn> COLUMNS = List.of(
            ImportColumn.required("name", "Например: Фильтр масляный", "Наименование", "Название"),
            ImportColumn.optional("catalogNumber", "Номер по каталогу. По нему (вместе с производителем) находится существующая позиция",
                    "Каталожный номер", "Номер по каталогу", "Артикул", "Part number", "P/N"),
            ImportColumn.optional("manufacturer", "Например: CAT, Komatsu, Shell", "Производитель", "Бренд"),
            ImportColumn.optional("unit", "шт, л, кг, м, компл.; по умолчанию — шт", "Ед. изм.", "Единица измерения", "Ед изм"),
            ImportColumn.optional("fluid", "да — жидкость на розлив (количество в литрах)", "Жидкость", "На розлив"),
            ImportColumn.optional("notes", "", "Примечание", "Комментарий"));

    private final PartService partService;
    private final PartRepository parts;
    private final UnitRepository units;

    public PartImportService(PartService partService, PartRepository parts, UnitRepository units) {
        this.partService = partService;
        this.parts = parts;
        this.units = units;
    }

    public byte[] template() {
        return ImportTemplateWriter.write("Запчасти", COLUMNS, List.of("Фильтр масляный", "1R-0750", "CAT", "шт", "нет", ""));
    }

    @PreAuthorize(PartService.CAN_EDIT)
    public ImportResult importParts(String filename, byte[] content) {
        List<ImportRow> rows = TabularFileReader.read(filename, content, COLUMNS);
        List<Unit> knownUnits = units.findAll();
        ImportResult result = new ImportResult();
        for (ImportRow row : rows) {
            try {
                String number = Strings.normalizeCode(row.text("catalogNumber"));
                String manufacturer = row.text("manufacturer");
                Optional<Part> existing = number != null
                        ? parts.findByCatalogNumber(number, manufacturer)
                        : parts.findByNameIgnoreCase(row.text("name"));
                PartCommand command = new PartCommand(null, existing.map(Part::version).orElse(null), row.text("name"),
                        number, manufacturer, parseUnit(row.text("unit"), knownUnits), parseFluid(row.text("fluid")),
                        row.text("notes"));
                if (existing.isPresent()) {
                    partService.update(existing.get().id(), command);
                    result.updated();
                } else {
                    partService.create(command);
                    result.created();
                }
            } catch (ImportRowException | BusinessRuleException | ConflictException | NotFoundException e) {
                result.error(row.rowNumber(), e.getMessage());
            }
        }
        return result;
    }

    static String parseUnit(String value, List<Unit> knownUnits) {
        if (value == null) {
            return "PCS";
        }
        String v = value.strip().replace(".", "").toLowerCase(Locale.ROOT);
        return knownUnits.stream()
                .filter(u -> u.code().equalsIgnoreCase(v) || u.name().replace(".", "").equalsIgnoreCase(v))
                .map(Unit::code)
                .findFirst()
                .orElseThrow(() -> new ImportRowException("Неизвестная единица измерения «" + value + "»"));
    }

    static Boolean parseFluid(String value) {
        if (value == null) {
            return null;
        }
        String v = value.strip().toLowerCase(Locale.ROOT);
        return v.equals("да") || v.equals("yes") || v.equals("true") || v.equals("1") || v.equals("+");
    }
}
