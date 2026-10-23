package com.company.acts.act.application;

import com.company.acts.act.application.port.out.ActRepository;
import com.company.acts.act.application.port.out.PartLineRepository;
import com.company.acts.act.application.port.out.TimeEntryRepository;
import com.company.acts.act.application.view.ActDetail;
import com.company.acts.act.domain.ActPolicy;
import com.company.acts.act.domain.TimeEntry;
import com.company.acts.act.domain.WorkAct;
import com.company.acts.act.domain.WorkPeriod;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import org.springframework.stereotype.Component;

/** Собирает карточку акта для API. */
@Component
class ActDetailAssembler {

    private final ActRepository acts;
    private final TimeEntryRepository timeEntries;
    private final PartLineRepository partLines;

    ActDetailAssembler(ActRepository acts, TimeEntryRepository timeEntries, PartLineRepository partLines) {
        this.acts = acts;
        this.timeEntries = timeEntries;
        this.partLines = partLines;
    }

    ActDetail assemble(ActContext ctx) {
        WorkAct act = ctx.act();
        ActPolicy policy = ctx.policy();
        List<TimeEntry> entries = timeEntries.findByAct(act.id()).stream()
                .sorted(Comparator.comparing(TimeEntry::workDate)).toList();
        WorkPeriod period = WorkPeriod.of(act, entries);
        Instant[] ts = acts.timestamps(act.id()).orElse(new Instant[] {null, null});

        var org = act.organization() == null ? null : new ActDetail.Organization(act.organization().id(),
                act.organization().name(), act.organization().shortName(), act.organization().inn(),
                act.organization().kpp(), act.organization().legalAddress(), act.organization().departmentId(),
                act.organization().departmentName());
        var eq = act.equipment() == null ? null : new ActDetail.Equipment(act.equipment().id(),
                act.equipment().typeName(), act.equipment().brand(), act.equipment().model(),
                act.equipment().serialNumber(), act.equipment().garageNumber(), act.equipment().plateNumber(),
                act.equipment().meterType());
        var engineers = ctx.participants().stream()
                .map(p -> new ActDetail.ParticipantView(p.engineerId(), p.fullName(), p.position(), p.role(),
                        sum(entries.stream().filter(e -> e.engineerId().equals(p.engineerId())).map(TimeEntry::hours)),
                        p.isUser(ctx.user())))
                .toList();
        var entryViews = entries.stream().map(e -> new ActDetail.TimeEntryView(e.id(), e.engineerId(), e.workDate(),
                e.hours(), e.travelHours(), e.comment())).toList();
        var parts = partLines.findByAct(act.id()).stream().map(l -> new ActDetail.PartLineView(l.id(), l.position(),
                l.source(), l.catalogItemId(), l.name(), l.catalogNumber(), l.quantity(), l.unit().name(),
                l.unit().label(), l.notes())).toList();
        var permissions = new ActDetail.Permissions(policy.canEdit(), policy.canEditAnyTime(), policy.canOpen(),
                policy.canClose(), policy.canReopen(), policy.canCancel(), policy.canDelete(),
                policy.requiresAdminComment());

        return new ActDetail(act.id(), act.version(), act.number(), act.status(), org, eq, act.meterHours(),
                act.meterKm(), act.location(), period.start(), period.end(),
                act.workStartOverride() != null || act.workEndOverride() != null, period.calendarDays(),
                period.workedDays(), act.shortDescription(), act.detailedDescription(), act.recommendations(),
                act.customerRepName(), act.customerRepPosition(), act.hideRequisites(), act.hideTravelTime(),
                act.authorEngineerId(), ts[0], ts[1], act.closedAt(), act.closedBy(), engineers, entryViews, parts,
                sum(entries.stream().map(TimeEntry::hours)),
                sum(entries.stream().map(TimeEntry::travelHours).filter(Objects::nonNull)),
                act.status().isEditable() ? act.missingForClose(!entries.isEmpty()) : List.of(), permissions);
    }

    private static BigDecimal sum(java.util.stream.Stream<BigDecimal> values) {
        return values.reduce(BigDecimal.ZERO, BigDecimal::add);
    }
}
