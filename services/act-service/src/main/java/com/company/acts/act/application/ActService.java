package com.company.acts.act.application;

import com.company.acts.act.application.command.ActCreateCommand;
import com.company.acts.act.application.command.ActHeaderCommand;
import com.company.acts.act.application.port.out.ActHistoryRepository;
import com.company.acts.act.application.port.out.ActNumberGenerator;
import com.company.acts.act.application.port.out.ActRepository;
import com.company.acts.act.application.port.out.CurrentUserProvider;
import com.company.acts.act.application.port.out.DirectoryClient;
import com.company.acts.act.application.port.out.EventPublisher;
import com.company.acts.act.application.port.out.ParticipantRepository;
import com.company.acts.act.application.port.out.PartLineRepository;
import com.company.acts.act.application.port.out.TimeEntryRepository;
import com.company.acts.act.application.view.ActDetail;
import com.company.acts.act.application.view.ActSearchQuery;
import com.company.acts.act.application.view.ActSummary;
import com.company.acts.act.application.view.HistoryEntry;
import com.company.acts.act.domain.ActStatus;
import com.company.acts.act.domain.CurrentUser;
import com.company.acts.act.domain.EquipmentSnapshot;
import com.company.acts.act.domain.MeterType;
import com.company.acts.act.domain.OrganizationSnapshot;
import com.company.acts.act.domain.PartLine;
import com.company.acts.act.domain.Participant;
import com.company.acts.act.domain.ParticipantRole;
import com.company.acts.act.domain.WorkAct;
import com.company.acts.common.core.BusinessRuleException;
import com.company.acts.common.core.ConflictException;
import com.company.acts.common.core.PageQuery;
import com.company.acts.common.core.PageResult;
import com.company.acts.common.core.Strings;
import com.company.acts.common.core.Uuid7;
import com.company.acts.common.events.EventTypes;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Сценарии жизненного цикла акта: создание, шапка, открытие, закрытие, возврат, аннулирование, копирование. */
@Service
public class ActService {

    private final ActRepository acts;
    private final ParticipantRepository participants;
    private final TimeEntryRepository timeEntries;
    private final PartLineRepository partLines;
    private final ActHistoryRepository history;
    private final ActNumberGenerator numbers;
    private final DirectoryClient directory;
    private final EventPublisher events;
    private final CurrentUserProvider users;
    private final ActLoader loader;
    private final ActDetailAssembler assembler;
    private final Clock clock;

    public ActService(ActRepository acts, ParticipantRepository participants, TimeEntryRepository timeEntries,
                      PartLineRepository partLines, ActHistoryRepository history, ActNumberGenerator numbers,
                      DirectoryClient directory, EventPublisher events, CurrentUserProvider users, ActLoader loader,
                      ActDetailAssembler assembler) {
        this.acts = acts;
        this.participants = participants;
        this.timeEntries = timeEntries;
        this.partLines = partLines;
        this.history = history;
        this.numbers = numbers;
        this.directory = directory;
        this.events = events;
        this.users = users;
        this.loader = loader;
        this.assembler = assembler;
        this.clock = Clock.systemDefaultZone();
    }

    @Transactional(readOnly = true)
    public PageResult<ActSummary> search(ActSearchQuery query, PageQuery page) {
        return acts.search(query, page);
    }

    /** Реестр «мои акты»: где текущий пользователь участвует. */
    @Transactional(readOnly = true)
    public PageResult<ActSummary> searchMine(ActSearchQuery query, PageQuery page) {
        CurrentUser user = users.current();
        return acts.search(new ActSearchQuery(query.text(), query.statuses(), query.organizationId(),
                query.equipmentId(), query.engineerId(), user.userId(), query.from(), query.to()), page);
    }

    @Transactional(readOnly = true)
    public ActDetail get(UUID id) {
        return assembler.assemble(loader.load(id));
    }

    @Transactional(readOnly = true)
    public List<HistoryEntry> history(UUID id) {
        loader.load(id);
        return history.findByAct(id);
    }

    @Transactional(readOnly = true)
    public List<String> shortDescriptions(String prefix) {
        return acts.shortDescriptions(Strings.trimToNull(prefix), 15);
    }

    /** Новый черновик: автор становится ответственным инженером акта. */
    @Transactional
    public ActDetail create(ActCreateCommand command) {
        if (command.id() != null && acts.findById(command.id()).isPresent()) {
            return get(command.id());
        }
        CurrentUser user = users.current();
        DirectoryClient.EngineerInfo author = directory.currentEngineer();
        WorkAct act = WorkAct.draft(command.id() != null ? command.id() : Uuid7.next(), author.id(), user.userId());
        if (command.equipmentId() != null) {
            Snapshots s = snapshots(command.equipmentId());
            act.updateHeader(s.organization(), s.equipment(), null, null, null, null, null, null, null, null, null,
                    null, true, true);
        }
        acts.save(act);
        participants.save(act.id(), new Participant(author.id(), author.keycloakUserId(), author.fullName(),
                author.position(), ParticipantRole.LEAD));
        acts.refreshAggregates(act.id());
        history.append(act.id(), "CREATED", null, user.username());
        events.publish(EventTypes.ACT_CREATED, act.id(), Map.of("status", act.status().name()));
        return get(act.id());
    }

    @Transactional
    public ActDetail updateHeader(UUID id, ActHeaderCommand c) {
        ActContext ctx = loader.load(id);
        ctx.require(ctx.policy().canEdit(), ctx.act().status() == ActStatus.CLOSED
                ? "Акт закрыт: изменить его может только администратор"
                : "Изменять акт могут его инженеры, руководитель и администратор");
        if (!Objects.equals(c.version(), ctx.act().version())) {
            throw ConflictException.staleVersion();
        }
        String adminComment = Strings.trimToNull(c.adminComment());
        if (ctx.policy().requiresAdminComment() && adminComment == null) {
            throw new BusinessRuleException("adminComment", "Укажите, почему изменяется закрытый акт");
        }
        WorkAct act = ctx.act();
        OrganizationSnapshot organization = act.organization();
        EquipmentSnapshot equipment = act.equipment();
        UUID currentEquipmentId = equipment == null ? null : equipment.id();
        if (!Objects.equals(c.equipmentId(), currentEquipmentId)) {
            if (c.equipmentId() == null) {
                if (act.status() != ActStatus.DRAFT) {
                    throw new BusinessRuleException("equipmentId", "У открытого акта техника обязательна");
                }
                organization = null;
                equipment = null;
            } else {
                Snapshots s = snapshots(c.equipmentId());
                organization = s.organization();
                equipment = s.equipment();
            }
        }
        act.updateHeader(organization, equipment, c.meterHours(), c.meterKm(), c.location(), c.workStartDate(),
                c.workEndDate(), c.shortDescription(), c.detailedDescription(), c.recommendations(),
                c.customerRepName(), c.customerRepPosition(), c.hideRequisites() == null || c.hideRequisites(),
                c.hideTravelTime() == null || c.hideTravelTime());
        acts.save(act);
        acts.refreshAggregates(id);
        history.append(id, act.status() == ActStatus.CLOSED ? "UPDATED_AFTER_CLOSE" : "UPDATED", adminComment,
                ctx.user().username());
        events.publish(EventTypes.ACT_UPDATED, id, Map.of("afterClose", act.status() == ActStatus.CLOSED));
        if (equipment != null && act.status() != ActStatus.DRAFT) {
            directory.raiseMeter(equipment.id(), act.meterHours(), act.meterKm());
        }
        return get(id);
    }

    /** Черновик → открыт: присваивается номер вида АВР-2026-000123. */
    @Transactional
    public ActDetail open(UUID id) {
        ActContext ctx = loader.load(id);
        ctx.require(ctx.policy().canOpen(), "Открыть этот акт вы не можете");
        WorkAct act = ctx.act();
        if (act.equipment() == null) {
            throw new BusinessRuleException("equipmentId", "Выберите технику, чтобы открыть акт");
        }
        act.open(numbers.next(LocalDate.now(clock).getYear()));
        acts.save(act);
        history.append(id, "OPENED", act.number(), ctx.user().username());
        events.publish(EventTypes.ACT_UPDATED, id, Map.of("status", "OPEN", "number", act.number()));
        return get(id);
    }

    @Transactional
    public ActDetail close(UUID id) {
        ActContext ctx = loader.load(id);
        ctx.require(ctx.policy().canClose(), "Закрыть акт могут его автор, руководитель и администратор");
        WorkAct act = ctx.act();
        act.close(timeEntries.existsByAct(id), ctx.user().username(), Instant.now(clock));
        acts.save(act);
        history.append(id, "CLOSED", null, ctx.user().username());
        List<PartLine> lines = partLines.findByAct(id);
        events.publish(EventTypes.ACT_CLOSED, id, Map.of("number", act.number()));
        if (!lines.isEmpty()) {
            events.publish(EventTypes.PARTS_USED_IN_ACT, id, Map.of("number", act.number(), "lines",
                    lines.stream().map(ActService::partPayload).toList()));
        }
        directory.raiseMeter(act.equipment().id(), act.meterHours(), act.meterKm());
        return get(id);
    }

    @Transactional
    public ActDetail reopen(UUID id, String comment) {
        ActContext ctx = loader.load(id);
        ctx.require(ctx.policy().canReopen(), "Вернуть закрытый акт в работу может только администратор");
        String text = Strings.trimToNull(comment);
        if (text == null) {
            throw new BusinessRuleException("comment", "Укажите причину возврата акта в работу");
        }
        ctx.act().reopen();
        acts.save(ctx.act());
        history.append(id, "REOPENED", text, ctx.user().username());
        events.publish(EventTypes.ACT_REOPENED, id, Map.of("comment", text));
        return get(id);
    }

    @Transactional
    public ActDetail cancel(UUID id, String comment) {
        ActContext ctx = loader.load(id);
        ctx.require(ctx.policy().canCancel(), "Аннулировать акт могут его автор, руководитель и администратор");
        String text = Strings.trimToNull(comment);
        if (text == null) {
            throw new BusinessRuleException("comment", "Укажите причину аннулирования");
        }
        ctx.act().cancel();
        acts.save(ctx.act());
        history.append(id, "CANCELLED", text, ctx.user().username());
        events.publish(EventTypes.ACT_CANCELLED, id, Map.of("comment", text));
        return get(id);
    }

    /** Удалить можно только черновик — у него ещё нет номера, поэтому в нумерации не остаётся пропусков. */
    @Transactional
    public void delete(UUID id) {
        ActContext ctx = loader.load(id);
        ctx.require(ctx.policy().canDelete(), "Удалить можно только свой черновик");
        acts.delete(id);
    }

    /** «Создать на основе»: копируются техника, описания, представитель заказчика и запчасти; время — нет. */
    @Transactional
    public ActDetail copy(UUID sourceId) {
        ActContext source = loader.load(sourceId);
        ActDetail created = create(new ActCreateCommand(null, null));
        WorkAct copy = acts.findById(created.id()).orElseThrow();
        WorkAct from = source.act();
        copy.updateHeader(from.organization(), from.equipment(), null, null, from.location(), null, null,
                from.shortDescription(), from.detailedDescription(), null, from.customerRepName(),
                from.customerRepPosition(), from.hideRequisites(), from.hideTravelTime());
        acts.save(copy);
        int position = 1;
        for (PartLine line : partLines.findByAct(sourceId)) {
            partLines.save(new PartLine(Uuid7.next(), copy.id(), position++, line.source(), line.catalogItemId(),
                    line.name(), line.catalogNumber(), line.quantity(), line.unit(), line.notes()));
        }
        acts.refreshAggregates(copy.id());
        history.append(copy.id(), "COPIED", from.number() != null ? from.number() : sourceId.toString(),
                source.user().username());
        return get(copy.id());
    }

    private record Snapshots(OrganizationSnapshot organization, EquipmentSnapshot equipment) {
    }

    private Snapshots snapshots(UUID equipmentId) {
        DirectoryClient.EquipmentInfo e = directory.equipment(equipmentId);
        DirectoryClient.OrganizationInfo o = directory.organization(e.organizationId());
        MeterType meterType = e.meterType() == null ? MeterType.HOURS : MeterType.valueOf(e.meterType());
        return new Snapshots(
                new OrganizationSnapshot(o.id(), o.name(), o.shortName(), o.inn(), o.kpp(), o.legalAddress(),
                        e.departmentId(), e.departmentName()),
                new EquipmentSnapshot(e.id(), e.equipmentTypeName(), e.brand(), e.model(), e.serialNumber(),
                        e.garageNumber(), e.plateNumber(), meterType));
    }

    private static Map<String, Object> partPayload(PartLine line) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("source", line.source().name());
        map.put("catalogItemId", line.catalogItemId() == null ? null : line.catalogItemId().toString());
        map.put("name", line.name());
        map.put("catalogNumber", line.catalogNumber());
        map.put("quantity", line.quantity());
        map.put("unit", line.unit().name());
        return map;
    }
}
