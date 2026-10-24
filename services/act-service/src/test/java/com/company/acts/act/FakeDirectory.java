package com.company.acts.act;

import com.company.acts.act.application.port.out.DirectoryClient;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.security.core.context.SecurityContextHolder;

/** Поддельный directory-service: инженер = логин пользователя, одна организация и единицы техники по запросу. */
class FakeDirectory implements DirectoryClient {

    static final UUID ORGANIZATION_ID = UUID.fromString("01910000-0000-7000-8000-00000000aaaa");
    static final UUID SITE_A = UUID.fromString("01980000-0000-7000-8000-00000000000a");
    static final UUID SITE_B = UUID.fromString("01980000-0000-7000-8000-00000000000b");
    static final UUID SITE_CLOSED = UUID.fromString("01980000-0000-7000-8000-00000000000c");
    static final UUID EXECUTOR_MAIN = UUID.fromString("01970000-0000-7000-8000-0000000000e1");
    static final UUID EXECUTOR_SECOND = UUID.fromString("01970000-0000-7000-8000-0000000000e2");

    final Map<UUID, EngineerInfo> engineers = new ConcurrentHashMap<>();
    final List<String> raisedMeters = new ArrayList<>();

    static UUID engineerId(String username) {
        return UUID.nameUUIDFromBytes(("engineer:" + username).getBytes(StandardCharsets.UTF_8));
    }

    EngineerInfo register(String username) {
        return engineers.computeIfAbsent(engineerId(username),
                id -> new EngineerInfo(id, username, "Инженер " + username, "Сервисный инженер", true));
    }

    @Override
    public EquipmentInfo equipment(UUID id) {
        return new EquipmentInfo(id, ORGANIZATION_ID, null, "Участок 1", SITE_A, "Карьерный самосвал", "CAT", "777G",
                "SN-" + id.toString().substring(0, 8), "45", null, "HOURS", BigDecimal.valueOf(1000), null);
    }

    @Override
    public OrganizationInfo organization(UUID id) {
        return new OrganizationInfo(id, "Акционерное общество «Тест»", "АО «Тест»", "7707083893", "773601001",
                "г. Москва");
    }

    @Override
    public EngineerInfo engineer(UUID id) {
        EngineerInfo info = engineers.get(id);
        if (info == null) {
            throw new com.company.acts.common.core.BusinessRuleException("engineerId", "Инженер не найден");
        }
        return info;
    }

    @Override
    public ExecutorInfo executor(UUID id) {
        if (EXECUTOR_SECOND.equals(id)) {
            return new ExecutorInfo(id, "ООО «Второй исполнитель»", "ООО «Второй»", "7707083893", null, null, null, true);
        }
        return defaultExecutor();
    }

    @Override
    public ExecutorInfo defaultExecutor() {
        return new ExecutorInfo(EXECUTOR_MAIN, "ООО «Сервисная компания»", null, null, null, null, null, true);
    }

    @Override
    public SiteInfo site(UUID id) {
        if (SITE_CLOSED.equals(id)) {
            return new SiteInfo(id, ORGANIZATION_ID, "Старый разрез", null, null, false);
        }
        return new SiteInfo(id, ORGANIZATION_ID, SITE_B.equals(id) ? "Площадка Б" : "Площадка А",
                SITE_B.equals(id) ? "Б" : null, "Воркута", true);
    }

    @Override
    public EngineerInfo currentEngineer() {
        return register(SecurityContextHolder.getContext().getAuthentication().getName());
    }

    @Override
    public void raiseMeter(UUID equipmentId, BigDecimal hours, BigDecimal km) {
        raisedMeters.add(equipmentId + ":" + hours);
    }
}
