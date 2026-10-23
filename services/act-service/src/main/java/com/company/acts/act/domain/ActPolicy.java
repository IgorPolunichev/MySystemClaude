package com.company.acts.act.domain;

import java.util.List;
import java.util.UUID;

/**
 * Права на действия с актом (раздел 11 SPEC.md, решение этапа 0 № 2): незакрытый акт правят автор,
 * участники, руководитель и администратор; закрытый — только администратор. Сервер проверяет права этим
 * классом и отдаёт результат клиенту, чтобы интерфейс показывал только доступные кнопки.
 */
public record ActPolicy(WorkAct act, List<Participant> participants, CurrentUser user) {

    public boolean isAuthor() {
        return user.userId() != null && user.userId().equals(act.authorUserId());
    }

    public boolean isParticipant() {
        return participants.stream().anyMatch(p -> p.isUser(user));
    }

    public boolean isLead() {
        return participants.stream().anyMatch(p -> p.role() == ParticipantRole.LEAD && p.isUser(user));
    }

    /** Править шапку, описания, запчасти, состав участников. */
    public boolean canEdit() {
        return switch (act.status()) {
            case DRAFT, OPEN -> isAuthor() || isParticipant() || user.isSupervisor();
            case CLOSED -> user.isAdmin();
            case CANCELLED -> false;
        };
    }

    /** Вносить время за себя. */
    public boolean canEditOwnTime() {
        return canEdit();
    }

    /** Вносить время за любого инженера акта: ответственный, автор, руководитель, администратор. */
    public boolean canEditAnyTime() {
        return canEdit() && (isAuthor() || isLead() || user.isSupervisor());
    }

    public boolean canEditTimeOf(UUID engineerId) {
        if (canEditAnyTime()) {
            return true;
        }
        return canEditOwnTime() && participants.stream()
                .anyMatch(p -> p.engineerId().equals(engineerId) && p.isUser(user));
    }

    public boolean canOpen() {
        return act.status() == ActStatus.DRAFT && canEdit();
    }

    /** Закрыть: автор, руководитель, администратор. */
    public boolean canClose() {
        return act.status() == ActStatus.OPEN && (isAuthor() || user.isSupervisor());
    }

    public boolean canReopen() {
        return act.status() == ActStatus.CLOSED && user.isAdmin();
    }

    public boolean canCancel() {
        return act.status() == ActStatus.OPEN && (isAuthor() || user.isSupervisor());
    }

    /** Удалить можно только черновик. */
    public boolean canDelete() {
        return act.status() == ActStatus.DRAFT && (isAuthor() || user.isSupervisor());
    }

    /** Изменение закрытого акта администратором требует комментария. */
    public boolean requiresAdminComment() {
        return act.status() == ActStatus.CLOSED;
    }
}
