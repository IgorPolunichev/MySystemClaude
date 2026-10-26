package com.company.acts.directory;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.company.acts.common.core.BusinessRuleException;
import com.company.acts.common.core.ConflictException;
import com.company.acts.common.core.PageQuery;
import com.company.acts.common.test.PostgresIntegrationTest;
import com.company.acts.directory.application.DepartmentService;
import com.company.acts.directory.application.DirectoryImportService;
import com.company.acts.directory.application.EngineerService;
import com.company.acts.directory.application.EquipmentService;
import com.company.acts.directory.application.EquipmentTypeService;
import com.company.acts.directory.application.ExecutorService;
import com.company.acts.directory.application.SiteService;
import com.company.acts.directory.application.command.ExecutorCommand;
import com.company.acts.directory.application.command.SiteCommand;
import com.company.acts.directory.application.OrganizationService;
import com.company.acts.directory.application.command.DepartmentCommand;
import com.company.acts.directory.application.command.EngineerCreateCommand;
import com.company.acts.directory.application.command.EquipmentCommand;
import com.company.acts.directory.application.command.NamedCommand;
import com.company.acts.directory.application.command.OrganizationCommand;
import com.company.acts.directory.domain.EngineerRole;
import com.company.acts.directory.domain.MeterType;
import com.company.acts.directory.domain.Organization;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.test.context.support.WithMockUser;

/** Сценарии справочников на реальном PostgreSQL: правила, права, поиск, импорт. */
@SpringBootTest(properties = "app.identity.keycloak.enabled=false")
class DirectoryScenariosIT extends PostgresIntegrationTest {

    @Autowired
    OrganizationService organizations;
    @Autowired
    DepartmentService departments;
    @Autowired
    EquipmentTypeService types;
    @Autowired
    EquipmentService equipment;
    @Autowired
    EngineerService engineers;
    @Autowired
    DirectoryImportService importService;
    @Autowired
    ExecutorService executors;
    @Autowired
    SiteService sites;

    @Test
    @WithMockUser(roles = "ADMIN")
    void organizationLifecycle() {
        String inn = randomInn();
        Organization created = organizations.create(org(null, null, "ООО «Альфа»", inn));
        assertThat(created.version()).isNotNull();

        assertThatThrownBy(() -> organizations.create(org(null, null, "ООО «Дубль»", inn)))
                .isInstanceOf(ConflictException.class).hasMessageContaining(inn);
        assertThatThrownBy(() -> organizations.create(org(null, null, "ООО «Ошибка»", "1234567890")))
                .isInstanceOf(BusinessRuleException.class);

        Organization updated = organizations.update(created.id(), org(null, created.version(), "ООО «Альфа-2»", inn));
        assertThat(updated.name()).isEqualTo("ООО «Альфа-2»");
        assertThat(updated.version()).isGreaterThan(created.version());

        assertThatThrownBy(() -> organizations.update(created.id(), org(null, created.version(), "Устаревшая", inn)))
                .isInstanceOf(ConflictException.class);

        UUID clientId = UUID.randomUUID();
        Organization first = organizations.create(org(clientId, null, "ООО «Идемпотент»", null));
        Organization repeat = organizations.create(org(clientId, null, "ООО «Идемпотент»", null));
        assertThat(repeat.id()).isEqualTo(first.id());

        assertThat(organizations.search("альфа-2", null, PageQuery.of(0, 10, null)).items())
                .extracting(Organization::id).contains(created.id());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void equipmentSearchByDepartmentSubtreeAndNumbers() {
        Organization o = organizations.create(org(null, null, "АО «Карьер»", randomInn()));
        var workshop = departments.create(new DepartmentCommand(null, null, o.id(), null, "Цех", null));
        var section = departments.create(new DepartmentCommand(null, null, o.id(), workshop.id(), "Участок", null));
        var type = types.create(new NamedCommand(null, null, "Тестовый самосвал " + UUID.randomUUID()));

        var truck = equipment.create(new EquipmentCommand(null, null, o.id(), section.id(), null, type.id(), "CAT", "777G",
                " cat0777xyz ", "45", null, MeterType.HOURS, new BigDecimal("1200.5"), null, 2020, null));
        assertThat(truck.serialNumber()).isEqualTo("CAT0777XYZ");
        assertThat(truck.organizationName()).isEqualTo("АО «Карьер»");
        assertThat(truck.departmentName()).isEqualTo("Участок");

        assertThat(equipment.search(null, null, workshop.id(), null, PageQuery.of(0, 10, null)).items())
                .extracting(v -> v.id()).containsExactly(truck.id());
        assertThat(equipment.search("777x", o.id(), null, null, PageQuery.of(0, 10, "-brand")).totalElements())
                .isEqualTo(1);

        assertThatThrownBy(() -> equipment.create(new EquipmentCommand(null, null, o.id(), null, null, type.id(), "CAT",
                "777G", "CAT0777XYZ", null, null, MeterType.HOURS, null, null, null, null)))
                .isInstanceOf(ConflictException.class);

        assertThatThrownBy(() -> departments.update(workshop.id(),
                new DepartmentCommand(null, workshop.version(), o.id(), section.id(), "Цех", null)))
                .isInstanceOf(BusinessRuleException.class);
        assertThatThrownBy(() -> organizations.delete(o.id())).isInstanceOf(ConflictException.class);
        assertThatThrownBy(() -> departments.delete(section.id())).isInstanceOf(ConflictException.class);

        equipment.delete(truck.id());
        departments.delete(section.id());
        departments.delete(workshop.id());
        organizations.delete(o.id());
    }

    @Test
    @WithMockUser(roles = "ENGINEER")
    void engineerCanAddButNotEdit() {
        Organization o = organizations.create(org(null, null, "ООО «Только добавить»", null));
        assertThatThrownBy(() -> organizations.update(o.id(), org(null, o.version(), "Новое имя", null)))
                .isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> organizations.delete(o.id())).isInstanceOf(AccessDeniedException.class);
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void engineerAccountIsLinkedOnFirstLogin() {
        String login = "user" + ThreadLocalRandom.current().nextInt(1_000_000);
        var created = engineers.create(new EngineerCreateCommand(null, login, "Secret123!", EngineerRole.ENGINEER,
                "Сидоров", "Пётр", "Ильич", "Инженер", null, null, null));
        assertThat(created.fullName()).isEqualTo("Сидоров П. И.");
        assertThat(created.keycloakUserId()).startsWith("local-");

        var me = engineers.current("kc-" + login, login.toUpperCase());
        assertThat(me.id()).isEqualTo(created.id());
        assertThat(me.keycloakUserId()).isEqualTo("kc-" + login);

        assertThatThrownBy(() -> engineers.create(new EngineerCreateCommand(null, login, "Secret123!",
                EngineerRole.ENGINEER, "Другой", "Человек", null, null, null, null, null)))
                .isInstanceOf(ConflictException.class);
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void importEquipmentCreatesThenUpdates() {
        String inn = randomInn();
        organizations.create(org(null, null, "ООО «Импорт»", inn));
        String csv = "Организация;Подразделение;Тип техники;Марка;Модель;Серийный номер;Моточасы\n"
                + inn + ";Новый цех;Импортный тип " + inn + ";CAT;777G;IMP-" + inn + ";1000\n"
                + "Нет такой;;Самосвал;CAT;777G;X1;1\n";

        var first = importService.importEquipment("import.csv", csv.getBytes(StandardCharsets.UTF_8));
        assertThat(first.getCreated()).isEqualTo(1);
        assertThat(first.getErrors()).singleElement().satisfies(e -> assertThat(e.row()).isEqualTo(3));

        var second = importService.importEquipment("import.csv",
                csv.replace(";1000\n", ";1500\n").getBytes(StandardCharsets.UTF_8));
        assertThat(second.getUpdated()).isEqualTo(1);
        assertThat(equipment.search("IMP-" + inn, null, null, null, PageQuery.of(0, 10, null)).items())
                .singleElement().satisfies(v -> assertThat(v.currentHours()).isEqualByComparingTo("1500"));
    }

    private static OrganizationCommand org(UUID id, Long version, String name, String inn) {
        return new OrganizationCommand(id, version, name, null, inn, null, null, null, null, null, null, null, null,
                null);
    }

    /** Случайный 10-значный ИНН с правильной контрольной цифрой. */
    static String randomInn() {
        int[] weights = {2, 4, 10, 3, 5, 9, 4, 6, 8};
        StringBuilder sb = new StringBuilder();
        int sum = 0;
        for (int i = 0; i < 9; i++) {
            int digit = i == 0 ? 1 + ThreadLocalRandom.current().nextInt(9) : ThreadLocalRandom.current().nextInt(10);
            sb.append(digit);
            sum += digit * weights[i];
        }
        return sb.append(sum % 11 % 10).toString();
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void executorsHaveExactlyOneDefault() {
        var base = executors.getDefault();
        var second = executors.create(new ExecutorCommand(null, null, "ООО «Второй исполнитель»", null, randomInn(),
                null, null, null, null, null, null, true, true));
        assertThat(executors.getDefault().id()).isEqualTo(second.id());
        assertThat(executors.get(base.id()).isDefault()).isFalse();
        assertThatThrownBy(() -> executors.update(second.id(), new ExecutorCommand(null, second.version(),
                second.name(), null, second.inn(), null, null, null, null, null, null, true, false)))
                .isInstanceOf(BusinessRuleException.class);
        var current = executors.get(base.id());
        executors.update(base.id(), new ExecutorCommand(null, current.version(), current.name(), current.shortName(),
                null, null, null, null, null, null, null, true, true));
        assertThat(executors.getDefault().id()).isEqualTo(base.id());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void blockedSiteCannotBeAssigned() {
        Organization o = organizations.create(org(null, null, "ООО «Площадки»", randomInn()));
        var a = sites.create(new SiteCommand(null, null, o.id(), "Площадка А", "а", null, true));
        var b = sites.create(new SiteCommand(null, null, o.id(), "Площадка Б", "02", "ул. Заводская, 1", true));
        assertThatThrownBy(() -> sites.create(new SiteCommand(null, null, o.id(), "площадка а", null, null, true)))
                .isInstanceOf(ConflictException.class);
        var type = types.create(new NamedCommand(null, null, "Тип для площадок " + UUID.randomUUID()));
        var truck = equipment.create(new EquipmentCommand(null, null, o.id(), null, a.id(), type.id(), "CAT", "777G",
                null, "77", null, MeterType.HOURS, null, null, null, null));
        assertThat(truck.siteName()).isEqualTo("Площадка А");

        assertThat(a.code()).isEqualTo("А");
        assertThatThrownBy(() -> sites.create(new SiteCommand(null, null, o.id(), "Площадка В", "02", null, true)))
                .isInstanceOf(ConflictException.class);
        sites.update(a.id(), new SiteCommand(null, a.version(), o.id(), a.name(), a.code(), null, false));
        assertThat(sites.listByOrganization(o.id(), false)).extracting(s -> s.name()).containsExactly("Площадка Б");
        assertThat(sites.listByOrganization(o.id(), true)).hasSize(2);
        // уже назначенную заблокированную площадку можно оставить, новую — нельзя
        var kept = equipment.update(truck.id(), new EquipmentCommand(null, truck.version(), o.id(), null, a.id(),
                type.id(), "CAT", "777G", null, "77", null, MeterType.HOURS, new BigDecimal("10"), null, null, null));
        assertThat(kept.siteId()).isEqualTo(a.id());
        assertThatThrownBy(() -> equipment.create(new EquipmentCommand(null, null, o.id(), null, a.id(), type.id(),
                "CAT", "785", null, "78", null, MeterType.HOURS, null, null, null, null)))
                .isInstanceOf(BusinessRuleException.class).hasMessageContaining("заблокирована");
        assertThat(b.active()).isTrue();
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void vinIsUniqueEverywhereAndGarageNumberPerDepartment() {
        Organization first = organizations.create(org(null, null, "ООО «Первая»", randomInn()));
        Organization second = organizations.create(org(null, null, "ООО «Вторая»", randomInn()));
        // участок А с подразделениями Б и В: в разных подразделениях гаражные номера могут совпадать
        var section = departments.create(new DepartmentCommand(null, null, first.id(), null, "Участок А", null));
        var b = departments.create(new DepartmentCommand(null, null, first.id(), section.id(), "Подразделение Б", null));
        var c = departments.create(new DepartmentCommand(null, null, first.id(), section.id(), "Подразделение В", null));
        var type = types.create(new NamedCommand(null, null, "Тип для дублей " + UUID.randomUUID()));
        String vin = "VIN" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();

        var truck = equipment.create(new EquipmentCommand(null, null, first.id(), b.id(), null, type.id(), "CAT", "777G",
                vin, "12", null, MeterType.HOURS, null, null, null, null));
        // VIN — во всей системе, даже в другой организации
        assertThatThrownBy(() -> equipment.create(new EquipmentCommand(null, null, second.id(), null, null, type.id(),
                "CAT", "777G", vin.toLowerCase(), null, null, MeterType.HOURS, null, null, null, null)))
                .isInstanceOf(ConflictException.class).hasMessageContaining("уже есть");
        // гаражный номер: в другом подразделении можно, в том же — нельзя
        equipment.create(new EquipmentCommand(null, null, first.id(), c.id(), null, type.id(), "Komatsu", "HD785",
                null, "12", null, MeterType.HOURS, null, null, null, null));
        assertThatThrownBy(() -> equipment.create(new EquipmentCommand(null, null, first.id(), b.id(), null, type.id(),
                "Volvo", "A40", null, "12", null, MeterType.HOURS, null, null, null, null)))
                .isInstanceOf(ConflictException.class).hasMessageContaining("Гаражный номер 12");

        // проверка до сохранения: найденная по VIN машина и предложение перенести её
        var found = equipment.duplicates(vin.toLowerCase(), "12", first.id(), b.id(), null);
        assertThat(found.bySerial().id()).isEqualTo(truck.id());
        assertThat(found.byGarage().id()).isEqualTo(truck.id());
        assertThat(equipment.duplicates(vin, "12", first.id(), b.id(), truck.id()).bySerial()).isNull();
        var moved = equipment.update(truck.id(), new EquipmentCommand(null, truck.version(), second.id(), null, null,
                type.id(), "CAT", "777G", vin, "12", null, MeterType.HOURS, null, null, null, null));
        assertThat(moved.organizationId()).isEqualTo(second.id());
    }
}
