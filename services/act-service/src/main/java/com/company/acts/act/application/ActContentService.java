package com.company.acts.act.application;

import com.company.acts.act.application.command.PartLineCommand;
import com.company.acts.act.application.command.TimeEntryCommand;
import com.company.acts.act.application.port.out.ActHistoryRepository;
import com.company.acts.act.application.port.out.ActRepository;
import com.company.acts.act.application.port.out.DirectoryClient;
import com.company.acts.act.application.port.out.EventPublisher;
import com.company.acts.act.application.port.out.ParticipantRepository;
import com.company.acts.act.application.port.out.PartLineRepository;
import com.company.acts.act.application.port.out.PartsClient;
import com.company.acts.act.application.port.out.TimeEntryRepository;
import com.company.acts.act.application.view.ActDetail;
import com.company.acts.act.domain.ActStatus;
import com.company.acts.act.domain.PartLine;
import com.company.acts.act.domain.PartSource;
import com.company.acts.act.domain.Participant;
import com.company.acts.act.domain.ParticipantRole;
import com.company.acts.act.domain.TimeEntry;
import com.company.acts.act.domain.UnitOfMeasure;
import com.company.acts.common.core.BusinessRuleException;
import com.company.acts.common.core.ConflictException;
import com.company.acts.common.core.NotFoundException;
import com.company.acts.common.core.Uuid7;
import com.company.acts.common.events.EventTypes;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Содержимое акта: участники, табель «инженер × день», запчасти. Каждая запись меняется отдельным
 * запросом и не трогает версию шапки — инженеры, работающие с одним актом, не мешают друг другу.
 */
@Service
public class ActContentService {

    static final BigDecimal DAILY_LIMIT = BigDecimal.valueOf(24);
    static final BigDecimal DAILY_WARNING = BigDecimal.valueOf(12);

    /** Итог изменения ячейки табеля; предупреждение — если за день у инженера больше 12 часов. */
    public record TimeEntryResult(ActDetail act, String warning) {
    }

    private final ActRepository acts;
    private final ParticipantRepository participants;
    private final TimeEntryRepository timeEntries;
    private final PartLineRepository partLines;
    private final ActHistoryRepository history;
    private final DirectoryClient directory;
    private final PartsClient partsCatalog;
    private final EventPublisher events;
    private final ActLoader loader;
    private final ActDetailAssembler assembler;

    public ActContentService(ActRepository acts, ParticipantRepository participants, TimeEntryRepository timeEntries,
                             PartLineRepository partLines, ActHistoryRepository history, DirectoryClient directory,
                             PartsClient partsCatalog, EventPublisher events, ActLoader loader,
                             ActDetailAssembler assembler) {
        this.acts = acts;
        this.participants = participants;
        this.timeEntries = timeEntries;
        this.partLines = partLines;
        this.history = history;
        this.directory = directory;
        this.partsCatalog = partsCatalog;
        this.events = events;
        this.loader = loader;
        this.assembler = assembler;
    }

    // ---------- Участники ----------

    @Transactional
    public ActDetail addEngineer(UUID actId, UUID engineerId) {
        ActContext ctx = editable(actId);
        if (ctx.participants().stream().anyMatch(p -> p.engineerId().equals(engineerId))) {
            return assembler.assemble(ctx);
        }
        DirectoryClient.EngineerInfo e = directory.engineer(engineerId);
        if (!e.active()) {
            throw new BusinessRuleException("engineerId", "Инженер заблокирован");
        }
        participants.save(actId, new Participant(e.id(), e.keycloakUserId(), e.fullName(), e.position(),
                ParticipantRole.MEMBER));
        acts.refreshAggregates(actId);
        history.append(actId, "ENGINEER_ADDED", e.fullName(), ctx.user().username());
        return assembler.assemble(loader.load(actId));
    }

    @Transactional
    public ActDetail removeEngineer(UUID actId, UUID engineerId) {
        ActContext ctx = editable(actId);
        Participant participant = ctx.participants().stream().filter(p -> p.engineerId().equals(engineerId))
                .findFirst().orElseThrow(() -> NotFoundException.of("Инженер в акте", engineerId));
        if (participant.role() == ParticipantRole.LEAD) {
            throw new ConflictException("Ответственного инженера убрать нельзя");
        }
        if (timeEntries.existsByActAndEngineer(actId, engineerId)) {
            throw new ConflictException("У инженера есть записи времени в этом акте — сначала удалите их");
        }
        participants.delete(actId, engineerId);
        acts.refreshAggregates(actId);
        history.append(actId, "ENGINEER_REMOVED", participant.fullName(), ctx.user().username());
        return assembler.assemble(loader.load(actId));
    }

    // ---------- Табель ----------

    /** Записать часы инженера за день (создать или заменить ячейку). */
    @Transactional
    public TimeEntryResult putTime(UUID actId, UUID engineerId, LocalDate date, TimeEntryCommand command) {
        ActContext ctx = editable(actId);
        ctx.require(ctx.policy().canEditTimeOf(engineerId),
                "Вносить время за других инженеров могут ответственный, автор акта, руководитель и администратор");
        if (ctx.participants().stream().noneMatch(p -> p.engineerId().equals(engineerId))) {
            throw new BusinessRuleException("engineerId", "Сначала добавьте инженера в акт");
        }
        var existing = timeEntries.find(actId, engineerId, date);
        TimeEntry entry = new TimeEntry(existing.map(TimeEntry::id).orElseGet(Uuid7::next), actId, engineerId, date,
                command.hours(), command.travelHours(), command.comment());
        BigDecimal otherHours = timeEntries.hoursOfEngineerOnDate(engineerId, date, entry.id());
        BigDecimal dayTotal = otherHours.add(entry.hours());
        if (dayTotal.compareTo(DAILY_LIMIT) > 0) {
            throw new BusinessRuleException("hours", "За " + date + " у инженера уже " + otherHours.stripTrailingZeros()
                    .toPlainString() + " ч по другим актам; всего за день не больше 24 ч");
        }
        timeEntries.save(entry);
        acts.refreshAggregates(actId);
        publishTime(actId, entry, entry.hours());
        String warning = dayTotal.compareTo(DAILY_WARNING) > 0
                ? "За " + date + " у инженера " + dayTotal.stripTrailingZeros().toPlainString() + " ч — проверьте"
                : null;
        return new TimeEntryResult(assembler.assemble(loader.load(actId)), warning);
    }

    @Transactional
    public ActDetail deleteTime(UUID actId, UUID engineerId, LocalDate date) {
        ActContext ctx = editable(actId);
        ctx.require(ctx.policy().canEditTimeOf(engineerId), "Удалять время других инженеров вы не можете");
        timeEntries.find(actId, engineerId, date).ifPresent(entry -> {
            timeEntries.delete(entry.id());
            acts.refreshAggregates(actId);
            publishTime(actId, entry, BigDecimal.ZERO);
        });
        return assembler.assemble(loader.load(actId));
    }

    // ---------- Запчасти ----------

    @Transactional
    public ActDetail addPart(UUID actId, PartLineCommand c) {
        ActContext ctx = editable(actId);
        if (c.id() != null && partLines.findById(c.id()).isPresent()) {
            return assembler.assemble(ctx);
        }
        PartLine line = toLine(c.id() != null ? c.id() : Uuid7.next(), actId, partLines.nextPosition(actId), c);
        partLines.save(line);
        history.append(actId, "PART_ADDED", line.name(), ctx.user().username());
        return assembler.assemble(loader.load(actId));
    }

    @Transactional
    public ActDetail updatePart(UUID actId, UUID lineId, PartLineCommand c) {
        ActContext ctx = editable(actId);
        PartLine current = partLines.findById(lineId).filter(l -> l.actId().equals(actId))
                .orElseThrow(() -> NotFoundException.of("Позиция", lineId));
        partLines.save(toLine(lineId, actId, current.position(), c));
        return assembler.assemble(ctx);
    }

    @Transactional
    public ActDetail deletePart(UUID actId, UUID lineId) {
        ActContext ctx = editable(actId);
        PartLine line = partLines.findById(lineId).filter(l -> l.actId().equals(actId))
                .orElseThrow(() -> NotFoundException.of("Позиция", lineId));
        partLines.delete(lineId);
        history.append(actId, "PART_REMOVED", line.name(), ctx.user().username());
        return assembler.assemble(loader.load(actId));
    }

    private PartLine toLine(UUID id, UUID actId, int position, PartLineCommand c) {
        UnitOfMeasure unit = UnitOfMeasure.of(c.unitCode());
        PartSource source = c.source();
        UUID catalogItemId = source == PartSource.CATALOG ? c.catalogItemId() : null;
        if (source == PartSource.MANUAL && Boolean.TRUE.equals(c.addToCatalog())) {
            catalogItemId = partsCatalog.createCatalogItem(c.name(), c.catalogNumber(), unit.name(),
                    unit == UnitOfMeasure.L);
            source = PartSource.CATALOG;
        }
        return new PartLine(id, actId, position, source, catalogItemId, c.name(), c.catalogNumber(), c.quantity(),
                unit, c.notes());
    }

    private ActContext editable(UUID actId) {
        ActContext ctx = loader.load(actId);
        ctx.require(ctx.policy().canEdit(), ctx.act().status() == ActStatus.CLOSED
                ? "Акт закрыт: изменить его может только администратор"
                : "Изменять акт могут его инженеры, руководитель и администратор");
        return ctx;
    }

    private void publishTime(UUID actId, TimeEntry entry, BigDecimal hours) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("engineerId", entry.engineerId().toString());
        payload.put("workDate", entry.workDate().toString());
        payload.put("hours", hours);
        payload.put("travelHours", entry.travelHours());
        events.publish(EventTypes.TIME_ENTRY_CHANGED, actId, payload);
    }
}
