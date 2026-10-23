package com.company.acts.directory.application;

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
import com.company.acts.directory.application.command.DepartmentCommand;
import com.company.acts.directory.application.command.EquipmentCommand;
import com.company.acts.directory.application.command.OrganizationCommand;
import com.company.acts.directory.application.port.out.DepartmentRepository;
import com.company.acts.directory.application.port.out.EquipmentRepository;
import com.company.acts.directory.application.port.out.OrganizationRepository;
import com.company.acts.directory.domain.Department;
import com.company.acts.directory.domain.Equipment;
import com.company.acts.directory.domain.MeterType;
import com.company.acts.directory.domain.Organization;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;

/**
 * Импорт организаций и техники из Excel/CSV. Каждая строка сохраняется в своей транзакции через
 * обычные сценарии (те же проверки, что и при ручном вводе): строка с ошибкой пропускается и попадает
 * в отчёт, остальные сохраняются. Существующие записи обновляются.
 */
@Service
public class DirectoryImportService {

    public static final List<ImportColumn> ORGANIZATION_COLUMNS = List.of(
            ImportColumn.required("name", "Полное наименование, например ООО «Ромашка»", "Наименование", "Название", "Организация"),
            ImportColumn.optional("shortName", "Как показывать в списках", "Краткое наименование", "Сокращённое наименование"),
            ImportColumn.optional("inn", "10 или 12 цифр. Если организация с таким ИНН уже есть — она обновится", "ИНН"),
            ImportColumn.optional("kpp", "9 символов", "КПП"),
            ImportColumn.optional("ogrn", "13 или 15 цифр", "ОГРН"),
            ImportColumn.optional("legalAddress", "", "Юридический адрес", "Адрес"),
            ImportColumn.optional("actualAddress", "", "Фактический адрес"),
            ImportColumn.optional("phone", "", "Телефон"),
            ImportColumn.optional("email", "", "Email", "Почта", "Электронная почта"),
            ImportColumn.optional("notes", "", "Примечание", "Комментарий"));

    public static final List<ImportColumn> EQUIPMENT_COLUMNS = List.of(
            ImportColumn.required("organization", "ИНН или наименование организации, которая уже есть в справочнике", "Организация", "Заказчик", "ИНН организации"),
            ImportColumn.optional("department", "Название подразделения; если такого нет — будет создано", "Подразделение", "Отдел", "Участок"),
            ImportColumn.required("type", "Например: Карьерный самосвал, Легковой автомобиль; новый тип будет создан", "Тип техники", "Тип"),
            ImportColumn.required("brand", "Например: CAT, Toyota", "Марка", "Производитель"),
            ImportColumn.required("model", "Например: 777G, Camry", "Модель"),
            ImportColumn.optional("serialNumber", "Серийный номер, VIN или PIN. По нему находится существующая техника", "Серийный номер", "Заводской номер", "VIN", "PIN"),
            ImportColumn.optional("garageNumber", "", "Гаражный номер", "Бортовой номер"),
            ImportColumn.optional("plateNumber", "", "Госномер", "Гос. номер", "Государственный номер"),
            ImportColumn.optional("meterType", "моточасы, км или оба; по умолчанию — моточасы", "Учёт наработки", "Наработка в"),
            ImportColumn.optional("hours", "Текущие моточасы", "Моточасы", "Наработка, м/ч"),
            ImportColumn.optional("km", "Текущий пробег", "Пробег", "Пробег, км"),
            ImportColumn.optional("year", "", "Год выпуска", "Год"),
            ImportColumn.optional("notes", "", "Примечание", "Комментарий"));

    private final OrganizationService organizationService;
    private final DepartmentService departmentService;
    private final EquipmentTypeService typeService;
    private final EquipmentService equipmentService;
    private final OrganizationRepository organizations;
    private final DepartmentRepository departments;
    private final EquipmentRepository equipment;

    public DirectoryImportService(OrganizationService organizationService, DepartmentService departmentService,
                                  EquipmentTypeService typeService, EquipmentService equipmentService,
                                  OrganizationRepository organizations, DepartmentRepository departments,
                                  EquipmentRepository equipment) {
        this.organizationService = organizationService;
        this.departmentService = departmentService;
        this.typeService = typeService;
        this.equipmentService = equipmentService;
        this.organizations = organizations;
        this.departments = departments;
        this.equipment = equipment;
    }

    public byte[] organizationTemplate() {
        return ImportTemplateWriter.write("Организации", ORGANIZATION_COLUMNS, List.of("ООО «Пример»", "Пример",
                "7707083893", "773601001", "1027700132195", "г. Москва, ул. Примерная, д. 1", "", "+7 900 000-00-00",
                "info@example.ru", ""));
    }

    public byte[] equipmentTemplate() {
        return ImportTemplateWriter.write("Техника", EQUIPMENT_COLUMNS, List.of("7707083893", "Автотранспортный цех",
                "Карьерный самосвал", "CAT", "777G", "CAT0777GTBY00123", "45", "", "моточасы", "12500", "", "2019", ""));
    }

    @PreAuthorize(Roles.CAN_EDIT)
    public ImportResult importOrganizations(String filename, byte[] content) {
        List<ImportRow> rows = TabularFileReader.read(filename, content, ORGANIZATION_COLUMNS);
        ImportResult result = new ImportResult();
        for (ImportRow row : rows) {
            try {
                Optional<Organization> existing = Optional.ofNullable(row.text("inn"))
                        .flatMap(organizations::findByInn)
                        .or(() -> organizations.findByNameIgnoreCase(row.text("name")));
                OrganizationCommand command = new OrganizationCommand(null,
                        existing.map(Organization::version).orElse(null), row.text("name"), row.text("shortName"),
                        row.text("inn"), row.text("kpp"), row.text("ogrn"), row.text("legalAddress"),
                        row.text("actualAddress"), existing.map(Organization::bankDetails).orElse(null),
                        row.text("phone"), row.text("email"), row.text("notes"), true);
                if (existing.isPresent()) {
                    organizationService.update(existing.get().id(), command);
                    result.updated();
                } else {
                    organizationService.create(command);
                    result.created();
                }
            } catch (ImportRowException | BusinessRuleException | ConflictException | NotFoundException e) {
                result.error(row.rowNumber(), e.getMessage());
            }
        }
        return result;
    }

    @PreAuthorize(Roles.CAN_EDIT)
    public ImportResult importEquipment(String filename, byte[] content) {
        List<ImportRow> rows = TabularFileReader.read(filename, content, EQUIPMENT_COLUMNS);
        ImportResult result = new ImportResult();
        Map<String, Organization> organizationCache = new HashMap<>();
        for (ImportRow row : rows) {
            try {
                Organization organization = organizationCache.computeIfAbsent(
                        row.text("organization").toLowerCase(Locale.ROOT), key -> findOrganization(row.text("organization")));
                UUID departmentId = row.text("department") == null ? null
                        : findOrCreateDepartment(organization.id(), row.text("department"));
                UUID typeId = typeService.findOrCreate(row.text("type")).id();
                String serial = Strings.normalizeCode(row.text("serialNumber"));
                String garage = Strings.normalizeCode(row.text("garageNumber"));
                String plate = row.text("plateNumber") == null ? null
                        : Strings.normalizeCode(row.text("plateNumber").replace(" ", ""));
                Optional<Equipment> existing = Optional.<Equipment>empty();
                if (serial != null) {
                    existing = equipment.findBySerial(organization.id(), serial);
                } else if (garage != null) {
                    existing = equipment.findByGarageNumber(organization.id(), garage);
                } else if (plate != null) {
                    existing = equipment.findByPlateNumber(organization.id(), plate);
                }
                EquipmentCommand command = new EquipmentCommand(null, existing.map(Equipment::version).orElse(null),
                        organization.id(), departmentId, typeId, row.text("brand"), row.text("model"), serial, garage,
                        plate, parseMeterType(row.text("meterType")), row.decimal("hours"), row.decimal("km"),
                        row.integer("year"), row.text("notes"));
                if (existing.isPresent()) {
                    equipmentService.update(existing.get().id(), command);
                    result.updated();
                } else {
                    equipmentService.create(command);
                    result.created();
                }
            } catch (ImportRowException | BusinessRuleException | ConflictException | NotFoundException e) {
                result.error(row.rowNumber(), e.getMessage());
            }
        }
        return result;
    }

    private Organization findOrganization(String value) {
        String key = value.strip();
        return organizations.findByInn(key)
                .or(() -> organizations.findByNameIgnoreCase(key))
                .orElseThrow(() -> new ImportRowException("Организация «" + key
                        + "» не найдена — сначала добавьте её в справочник или импортируйте"));
    }

    private UUID findOrCreateDepartment(UUID organizationId, String name) {
        return departments.findByOrganization(organizationId).stream()
                .filter(d -> d.name().equalsIgnoreCase(name.strip()))
                .map(Department::id)
                .findFirst()
                .orElseGet(() -> departmentService.create(new DepartmentCommand(null, null, organizationId, null,
                        name, null)).id());
    }

    static MeterType parseMeterType(String value) {
        if (value == null) {
            return MeterType.HOURS;
        }
        String v = value.strip().toLowerCase(Locale.ROOT);
        if (v.startsWith("мото") || v.equals("м/ч") || v.equals("мч") || v.equals("hours")) {
            return MeterType.HOURS;
        }
        if (v.startsWith("км") || v.startsWith("пробег") || v.equals("km")) {
            return MeterType.KM;
        }
        if (v.startsWith("оба") || v.equals("both")) {
            return MeterType.BOTH;
        }
        throw new ImportRowException("Учёт наработки: «" + value + "» — укажите моточасы, км или оба");
    }
}
