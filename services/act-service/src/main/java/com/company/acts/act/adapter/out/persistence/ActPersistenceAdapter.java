package com.company.acts.act.adapter.out.persistence;

import com.company.acts.act.application.port.out.ActNumberGenerator;
import com.company.acts.act.application.port.out.ActRepository;
import com.company.acts.act.application.view.ActSearchQuery;
import com.company.acts.act.application.view.ActSummary;
import com.company.acts.act.domain.ActNumberFormat;
import com.company.acts.act.domain.EquipmentSnapshot;
import com.company.acts.act.domain.OrganizationSnapshot;
import com.company.acts.act.domain.WorkAct;
import com.company.acts.common.core.ConflictException;
import com.company.acts.common.core.PageQuery;
import com.company.acts.common.core.PageResult;
import com.company.acts.common.persistence.JpqlFilter;
import com.company.acts.common.persistence.SortWhitelist;
import jakarta.persistence.EntityManager;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
class ActPersistenceAdapter implements ActRepository, ActNumberGenerator {

    private static final SortWhitelist SORT = new SortWhitelist(Map.of(
            "number", "e.number",
            "createdAt", "e.createdAt",
            "updatedAt", "e.updatedAt",
            "periodStart", "e.periodStart",
            "organizationName", "lower(coalesce(e.orgShortName, e.orgName))",
            "shortDescription", "lower(e.shortDescription)",
            "totalHours", "e.totalHours",
            "status", "e.status"), "e.createdAt");

    private final WorkActJpaRepository repository;
    private final EntityManager em;
    private final JdbcTemplate jdbc;
    private final String numberPrefix;

    ActPersistenceAdapter(WorkActJpaRepository repository, EntityManager em, JdbcTemplate jdbc,
                          @Value("${app.acts.number-prefix:АВР}") String numberPrefix) {
        this.repository = repository;
        this.em = em;
        this.jdbc = jdbc;
        this.numberPrefix = numberPrefix;
    }

    @Override
    public Optional<WorkAct> findById(UUID id) {
        return repository.findById(id).map(ActPersistenceAdapter::toDomain);
    }

    @Override
    public Optional<Instant[]> timestamps(UUID id) {
        return repository.findById(id).map(e -> new Instant[] {e.getCreatedAt(), e.getUpdatedAt()});
    }

    @Override
    public WorkAct save(WorkAct a) {
        WorkActJpaEntity e = repository.findById(a.id()).orElseGet(() -> {
            WorkActJpaEntity created = new WorkActJpaEntity();
            created.setId(a.id());
            return created;
        });
        if (a.version() != null && !Objects.equals(a.version(), e.getVersion())) {
            throw ConflictException.staleVersion();
        }
        e.setNumber(a.number());
        e.setStatus(a.status());
        OrganizationSnapshot o = a.organization();
        e.setOrgId(o == null ? null : o.id());
        e.setOrgName(o == null ? null : o.name());
        e.setOrgShortName(o == null ? null : o.shortName());
        e.setOrgInn(o == null ? null : o.inn());
        e.setOrgKpp(o == null ? null : o.kpp());
        e.setOrgLegalAddress(o == null ? null : o.legalAddress());
        e.setDepartmentId(o == null ? null : o.departmentId());
        e.setDepartmentName(o == null ? null : o.departmentName());
        EquipmentSnapshot q = a.equipment();
        e.setEquipmentId(q == null ? null : q.id());
        e.setEquipmentTypeName(q == null ? null : q.typeName());
        e.setEquipmentBrand(q == null ? null : q.brand());
        e.setEquipmentModel(q == null ? null : q.model());
        e.setEquipmentSerial(q == null ? null : q.serialNumber());
        e.setEquipmentGarage(q == null ? null : q.garageNumber());
        e.setEquipmentPlate(q == null ? null : q.plateNumber());
        e.setEquipmentMeterType(q == null ? null : q.meterType());
        e.setMeterHours(a.meterHours());
        e.setMeterKm(a.meterKm());
        e.setLocation(a.location());
        e.setWorkStartOverride(a.workStartOverride());
        e.setWorkEndOverride(a.workEndOverride());
        e.setShortDescription(a.shortDescription());
        e.setDetailedDescription(a.detailedDescription());
        e.setRecommendations(a.recommendations());
        e.setCustomerRepName(a.customerRepName());
        e.setCustomerRepPosition(a.customerRepPosition());
        e.setHideRequisites(a.hideRequisites());
        e.setHideTravelTime(a.hideTravelTime());
        e.setAuthorEngineerId(a.authorEngineerId());
        e.setAuthorUserId(a.authorUserId());
        e.setClosedAt(a.closedAt());
        e.setClosedBy(a.closedBy());
        return toDomain(repository.saveAndFlush(e));
    }

    @Override
    public void delete(UUID id) {
        repository.findById(id).ifPresent(e -> {
            repository.delete(e);
            repository.flush();
        });
    }

    @Override
    public void refreshAggregates(UUID id) {
        em.flush();
        jdbc.update("""
                update work_act w set
                    total_hours = round(coalesce((select sum(t.work_minutes) from time_entry t where t.act_id = w.id), 0) / 60.0, 2),
                    period_start = coalesce(w.work_start_override,
                        (select min(t.work_date) from time_entry t where t.act_id = w.id)),
                    period_end = coalesce(w.work_end_override,
                        (select max(t.work_date) from time_entry t where t.act_id = w.id)),
                    engineer_names = (select string_agg(p.full_name, ', ' order by p.role, p.full_name)
                        from act_participant p where p.act_id = w.id)
                where w.id = ?
                """, id);
    }

    @Override
    public PageResult<ActSummary> search(ActSearchQuery q, PageQuery page) {
        JpqlFilter filter = new JpqlFilter()
                .where("e.status in :statuses", "statuses", q.statuses() == null || q.statuses().isEmpty() ? null : q.statuses())
                .where("e.orgId = :organizationId", "organizationId", q.organizationId())
                .where("e.equipmentId = :equipmentId", "equipmentId", q.equipmentId())
                .where("exists (select 1 from ParticipantJpaEntity p where p.actId = e.id and p.engineerId = :engineerId)",
                        "engineerId", q.engineerId())
                .where("exists (select 1 from ParticipantJpaEntity p2 where p2.actId = e.id and p2.keycloakUserId = :userId)",
                        "userId", q.participantUserId())
                .where("(e.periodEnd >= :from or (e.periodEnd is null and e.createdAt >= :fromInstant))", "from", q.from())
                .where("(e.periodStart <= :to or (e.periodStart is null and e.createdAt < :toInstant))", "to", q.to())
                .search(q.text(), "coalesce(e.number, '')", "coalesce(e.shortDescription, '')",
                        "coalesce(e.orgName, '')", "coalesce(e.orgShortName, '')", "coalesce(e.equipmentBrand, '')",
                        "coalesce(e.equipmentModel, '')", "coalesce(e.equipmentSerial, '')",
                        "coalesce(e.equipmentGarage, '')", "coalesce(e.equipmentPlate, '')",
                        "coalesce(e.engineerNames, '')");
        String sortField = page.sortField() == null ? "createdAt" : page.sortField();
        boolean ascending = page.sortField() != null && page.ascending();
        var countQuery = filter.query(em, "select count(e) from WorkActJpaEntity e" + filter.clause(), Long.class);
        var listQuery = filter.query(em, "select e from WorkActJpaEntity e" + filter.clause()
                + SORT.orderBy(sortField, ascending), WorkActJpaEntity.class);
        if (q.from() != null) {
            Instant from = q.from().atStartOfDay(ZoneOffset.UTC).toInstant();
            countQuery.setParameter("fromInstant", from);
            listQuery.setParameter("fromInstant", from);
        }
        if (q.to() != null) {
            Instant to = q.to().plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant();
            countQuery.setParameter("toInstant", to);
            listQuery.setParameter("toInstant", to);
        }
        long total = countQuery.getSingleResult();
        List<ActSummary> items = listQuery.setFirstResult(page.offset()).setMaxResults(page.size()).getResultList()
                .stream().map(ActPersistenceAdapter::toSummary).toList();
        return new PageResult<>(items, page.page(), page.size(), total);
    }

    @Override
    public List<String> shortDescriptions(String prefix, int limit) {
        JpqlFilter filter = new JpqlFilter().where("e.shortDescription is not null").search(prefix, "e.shortDescription");
        return filter.query(em, "select distinct e.shortDescription from WorkActJpaEntity e" + filter.clause()
                        + " order by e.shortDescription", String.class)
                .setMaxResults(limit).getResultList();
    }

    /** Номер выдаёт одна атомарная команда PostgreSQL: одновременные запросы получают разные номера. */
    @Override
    public String next(int year) {
        Long value = jdbc.queryForObject("""
                insert into act_number_sequence (year, prefix, last_value) values (?, ?, 1)
                on conflict (year, prefix) do update set last_value = act_number_sequence.last_value + 1
                returning last_value
                """, Long.class, year, numberPrefix);
        return ActNumberFormat.format(numberPrefix, year, value == null ? 1 : value);
    }

    static WorkAct toDomain(WorkActJpaEntity e) {
        OrganizationSnapshot organization = e.getOrgId() == null ? null : new OrganizationSnapshot(e.getOrgId(),
                e.getOrgName(), e.getOrgShortName(), e.getOrgInn(), e.getOrgKpp(), e.getOrgLegalAddress(),
                e.getDepartmentId(), e.getDepartmentName());
        EquipmentSnapshot equipment = e.getEquipmentId() == null ? null : new EquipmentSnapshot(e.getEquipmentId(),
                e.getEquipmentTypeName(), e.getEquipmentBrand(), e.getEquipmentModel(), e.getEquipmentSerial(),
                e.getEquipmentGarage(), e.getEquipmentPlate(), e.getEquipmentMeterType());
        return new WorkAct(e.getId(), e.getVersion(), e.getNumber(), e.getStatus(), organization, equipment,
                e.getMeterHours(), e.getMeterKm(), e.getLocation(), e.getWorkStartOverride(), e.getWorkEndOverride(),
                e.getShortDescription(), e.getDetailedDescription(), e.getRecommendations(), e.getCustomerRepName(),
                e.getCustomerRepPosition(), e.isHideRequisites(), e.isHideTravelTime(), e.getAuthorEngineerId(),
                e.getAuthorUserId(), e.getClosedAt(), e.getClosedBy());
    }

    private static ActSummary toSummary(WorkActJpaEntity e) {
        List<String> numbers = new ArrayList<>();
        if (e.getEquipmentGarage() != null) {
            numbers.add("гар. № " + e.getEquipmentGarage());
        }
        if (e.getEquipmentPlate() != null) {
            numbers.add(e.getEquipmentPlate());
        }
        if (numbers.isEmpty() && e.getEquipmentSerial() != null) {
            numbers.add("S/N " + e.getEquipmentSerial());
        }
        String title = e.getEquipmentId() == null ? null : e.getEquipmentBrand() + " " + e.getEquipmentModel();
        return new ActSummary(e.getId(), e.getNumber(), e.getStatus(),
                e.getOrgShortName() != null ? e.getOrgShortName() : e.getOrgName(), e.getDepartmentName(), title,
                String.join(" · ", numbers), e.getShortDescription(), e.getPeriodStart(), e.getPeriodEnd(),
                e.getTotalHours(), e.getEngineerNames(), e.getCreatedAt(), e.getUpdatedAt());
    }
}
