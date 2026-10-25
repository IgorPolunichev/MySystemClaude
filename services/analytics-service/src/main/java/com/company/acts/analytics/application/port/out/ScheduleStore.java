package com.company.acts.analytics.application.port.out;

import com.company.acts.analytics.domain.schedule.Member;
import com.company.acts.analytics.domain.schedule.ScheduleTemplate;
import com.company.acts.analytics.domain.schedule.ShiftKind;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/** Шаблоны, группы, составы бригад и ручные отметки графиков. */
public interface ScheduleStore {

    record StoredTemplate(UUID id, ScheduleTemplate template) {
    }

    record Group(UUID id, String name, UUID departmentId, UUID siteId, String siteName, UUID templateId,
                 LocalDate startDate, int requiredDay, int requiredNight, Instant approvedAt, String approvedBy,
                 long version) {
    }

    record Mark(UUID employeeId, LocalDate day, ShiftKind kind, String comment) {
    }

    List<StoredTemplate> templates();

    Optional<StoredTemplate> template(UUID id);

    void saveTemplate(UUID id, ScheduleTemplate template, String user);

    boolean templateInUse(UUID id);

    void deleteTemplate(UUID id);

    List<Group> groups();

    Optional<Group> group(UUID id);

    /** Сохранить группу; версия проверяется (оптимистичная блокировка), возвращает новую версию. */
    long saveGroup(Group group);

    void deleteGroup(UUID id);

    List<Member> members(UUID groupId);

    void replaceMembers(UUID groupId, List<Member> members);

    /** Отметки сотрудников группы за период. */
    Map<UUID, Map<LocalDate, ShiftKind>> overrides(UUID groupId, LocalDate from, LocalDate to);

    List<Mark> overrideList(UUID groupId, LocalDate from, LocalDate to);

    /** kind = null — снять отметку. */
    void setOverride(UUID groupId, UUID employeeId, LocalDate day, ShiftKind kind, String comment, String user);

    /** Группы, в которых состоит сотрудник (для норм и контроля превышений). */
    List<UUID> groupsOf(UUID employeeId);
}
