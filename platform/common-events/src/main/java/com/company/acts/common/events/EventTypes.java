package com.company.acts.common.events;

/**
 * Реестр типов событий (раздел 4 SPEC.md). Полезные нагрузки добавляются на этапах, где события начинают
 * публиковаться; имена фиксируются уже сейчас, чтобы будущие модули (уведомления, склад) могли на них опираться.
 */
public final class EventTypes {

    // act-service
    public static final String ACT_CREATED = "ActCreated";
    public static final String ACT_UPDATED = "ActUpdated";
    public static final String ACT_CLOSED = "ActClosed";
    public static final String ACT_REOPENED = "ActReopened";
    public static final String ACT_CANCELLED = "ActCancelled";
    public static final String ACT_DELETED = "ActDeleted";
    public static final String TIME_ENTRY_CHANGED = "TimeEntryChanged";
    public static final String PARTS_USED_IN_ACT = "PartsUsedInAct";

    // directory-service
    public static final String EQUIPMENT_UPDATED = "EquipmentUpdated";
    public static final String ORGANIZATION_UPDATED = "OrganizationUpdated";

    // mail-service
    public static final String EMAIL_SENT = "EmailSent";

    // document-service
    public static final String DOCUMENT_GENERATED = "DocumentGenerated";

    private EventTypes() {
    }
}
