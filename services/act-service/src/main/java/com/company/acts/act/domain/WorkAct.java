package com.company.acts.act.domain;

import com.company.acts.common.core.BusinessRuleException;
import com.company.acts.common.core.ConflictException;
import com.company.acts.common.core.Strings;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Акт выполненных работ — шапка и статус. Записи времени, запчасти и участники — отдельные записи
 * (чтобы инженеры не мешали друг другу), правила над ними — в {@link ActPolicy}.
 */
public final class WorkAct {

    private final UUID id;
    private final Long version;
    private String number;
    private ActStatus status;
    private OrganizationSnapshot organization;
    private EquipmentSnapshot equipment;
    private BigDecimal meterHours;
    private BigDecimal meterKm;
    private String location;
    private LocalDate workStartOverride;
    private LocalDate workEndOverride;
    private String shortDescription;
    private String detailedDescription;
    private String recommendations;
    private String customerRepName;
    private String customerRepPosition;
    private boolean hideRequisites;
    private boolean hideTravelTime;
    private final UUID authorEngineerId;
    private final String authorUserId;
    private Instant closedAt;
    private String closedBy;

    public WorkAct(UUID id, Long version, String number, ActStatus status, OrganizationSnapshot organization,
                   EquipmentSnapshot equipment, BigDecimal meterHours, BigDecimal meterKm, String location,
                   LocalDate workStartOverride, LocalDate workEndOverride, String shortDescription,
                   String detailedDescription, String recommendations, String customerRepName,
                   String customerRepPosition, boolean hideRequisites, boolean hideTravelTime,
                   UUID authorEngineerId, String authorUserId, Instant closedAt, String closedBy) {
        this.id = Objects.requireNonNull(id, "id");
        this.version = version;
        this.number = number;
        this.status = Objects.requireNonNull(status, "status");
        this.organization = organization;
        this.equipment = equipment;
        this.meterHours = meterHours;
        this.meterKm = meterKm;
        this.location = location;
        this.workStartOverride = workStartOverride;
        this.workEndOverride = workEndOverride;
        this.shortDescription = shortDescription;
        this.detailedDescription = detailedDescription;
        this.recommendations = recommendations;
        this.customerRepName = customerRepName;
        this.customerRepPosition = customerRepPosition;
        this.hideRequisites = hideRequisites;
        this.hideTravelTime = hideTravelTime;
        this.authorEngineerId = Objects.requireNonNull(authorEngineerId, "authorEngineerId");
        this.authorUserId = authorUserId;
        this.closedAt = closedAt;
        this.closedBy = closedBy;
    }

    /** Новый черновик: реквизиты и время в пути по умолчанию скрыты в печатной форме (раздел 16 SPEC.md). */
    public static WorkAct draft(UUID id, UUID authorEngineerId, String authorUserId) {
        return new WorkAct(id, null, null, ActStatus.DRAFT, null, null, null, null, null, null, null, null, null,
                null, null, null, true, true, authorEngineerId, authorUserId, null, null);
    }

    /** Изменение шапки и описаний. Снимки техники и организации передаются уже собранными. */
    public void updateHeader(OrganizationSnapshot organization, EquipmentSnapshot equipment, BigDecimal meterHours,
                             BigDecimal meterKm, String location, LocalDate workStartOverride,
                             LocalDate workEndOverride, String shortDescription, String detailedDescription,
                             String recommendations, String customerRepName, String customerRepPosition,
                             boolean hideRequisites, boolean hideTravelTime) {
        if (meterHours != null && meterHours.signum() < 0) {
            throw new BusinessRuleException("meterHours", "Моточасы не могут быть отрицательными");
        }
        if (meterKm != null && meterKm.signum() < 0) {
            throw new BusinessRuleException("meterKm", "Пробег не может быть отрицательным");
        }
        if (workStartOverride != null && workEndOverride != null && workEndOverride.isBefore(workStartOverride)) {
            throw new BusinessRuleException("workEndDate", "Дата окончания раньше даты начала");
        }
        String shortText = Strings.trimToNull(shortDescription);
        if (shortText != null && shortText.length() > 100) {
            throw new BusinessRuleException("shortDescription", "Краткое описание — не длиннее 100 символов");
        }
        this.organization = organization;
        this.equipment = equipment;
        this.meterHours = meterHours;
        this.meterKm = meterKm;
        this.location = Strings.trimToNull(location);
        this.workStartOverride = workStartOverride;
        this.workEndOverride = workEndOverride;
        this.shortDescription = shortText;
        this.detailedDescription = Strings.trimToNull(detailedDescription);
        this.recommendations = Strings.trimToNull(recommendations);
        this.customerRepName = Strings.trimToNull(customerRepName);
        this.customerRepPosition = Strings.trimToNull(customerRepPosition);
        this.hideRequisites = hideRequisites;
        this.hideTravelTime = hideTravelTime;
    }

    /** Черновик → открыт: присваивается номер. Без техники открыть нельзя. */
    public void open(String assignedNumber) {
        if (status != ActStatus.DRAFT) {
            throw new ConflictException("Открыть можно только черновик");
        }
        if (equipment == null) {
            throw new BusinessRuleException("equipmentId", "Выберите технику, чтобы открыть акт");
        }
        this.number = Objects.requireNonNull(assignedNumber, "assignedNumber");
        this.status = ActStatus.OPEN;
    }

    /** Что не заполнено для закрытия (раздел 11 SPEC.md); пустой список — можно закрывать. */
    public List<String> missingForClose(boolean hasTimeEntries) {
        return missingForClose(hasTimeEntries, 0);
    }

    /** То же с учётом фото: у каждого фото техотчёта должна быть подпись (раздел 6.6 SPEC.md). */
    public List<String> missingForClose(boolean hasTimeEntries, int photosWithoutCaption) {
        List<String> missing = new ArrayList<>();
        if (equipment == null) {
            missing.add("техника");
        } else {
            if (equipment.meterType().usesHours() && meterHours == null) {
                missing.add("моточасы");
            }
            if (equipment.meterType().usesKm() && meterKm == null) {
                missing.add("пробег");
            }
        }
        if (shortDescription == null) {
            missing.add("краткое описание работ");
        }
        if (!hasTimeEntries) {
            missing.add("хотя бы одна запись времени");
        }
        if (photosWithoutCaption > 0) {
            missing.add("подписи к фото (без подписи: " + photosWithoutCaption + ")");
        }
        return missing;
    }

    public void close(boolean hasTimeEntries, String by, Instant at) {
        close(hasTimeEntries, 0, by, at);
    }

    public void close(boolean hasTimeEntries, int photosWithoutCaption, String by, Instant at) {
        if (status != ActStatus.OPEN) {
            throw new ConflictException("Закрыть можно только открытый акт");
        }
        List<String> missing = missingForClose(hasTimeEntries, photosWithoutCaption);
        if (!missing.isEmpty()) {
            throw new BusinessRuleException("Чтобы закрыть акт, заполните: " + String.join(", ", missing));
        }
        this.status = ActStatus.CLOSED;
        this.closedAt = at;
        this.closedBy = by;
    }

    public void reopen() {
        if (status != ActStatus.CLOSED) {
            throw new ConflictException("Вернуть в работу можно только закрытый акт");
        }
        this.status = ActStatus.OPEN;
        this.closedAt = null;
        this.closedBy = null;
    }

    public void cancel() {
        if (status != ActStatus.OPEN) {
            throw new ConflictException(status == ActStatus.DRAFT
                    ? "Черновик не аннулируют, а удаляют"
                    : "Аннулировать можно только открытый акт");
        }
        this.status = ActStatus.CANCELLED;
    }

    public UUID id() {
        return id;
    }

    public Long version() {
        return version;
    }

    public String number() {
        return number;
    }

    public ActStatus status() {
        return status;
    }

    public OrganizationSnapshot organization() {
        return organization;
    }

    public EquipmentSnapshot equipment() {
        return equipment;
    }

    public BigDecimal meterHours() {
        return meterHours;
    }

    public BigDecimal meterKm() {
        return meterKm;
    }

    public String location() {
        return location;
    }

    public LocalDate workStartOverride() {
        return workStartOverride;
    }

    public LocalDate workEndOverride() {
        return workEndOverride;
    }

    public String shortDescription() {
        return shortDescription;
    }

    public String detailedDescription() {
        return detailedDescription;
    }

    public String recommendations() {
        return recommendations;
    }

    public String customerRepName() {
        return customerRepName;
    }

    public String customerRepPosition() {
        return customerRepPosition;
    }

    public boolean hideRequisites() {
        return hideRequisites;
    }

    public boolean hideTravelTime() {
        return hideTravelTime;
    }

    public UUID authorEngineerId() {
        return authorEngineerId;
    }

    public String authorUserId() {
        return authorUserId;
    }

    public Instant closedAt() {
        return closedAt;
    }

    public String closedBy() {
        return closedBy;
    }
}
