package com.company.acts.act.adapter.out.persistence;

import com.company.acts.act.application.port.out.ActNumberGenerator;
import com.company.acts.act.application.port.out.ActRepository;
import com.company.acts.act.application.view.ActSearchQuery;
import com.company.acts.act.application.view.ActSummary;
import com.company.acts.act.domain.ActNumberFormat;
import com.company.acts.act.domain.EquipmentSnapshot;
import com.company.acts.act.domain.ExecutorSnapshot;
import com.company.acts.act.domain.SiteSnapshot;
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
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
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
        return repository.findById(id).map(e -> new Instant[] {e.getCreatedAt(), e.getUpdatedAt(), e.getLastSentAt()});
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
        ExecutorSnapshot x = a.executor();
        e.setExecutorId(x == null ? null : x.id());
        e.setExecutorName(x == null ? null : x.name());
        e.setExecutorShortName(x == null ? null : x.shortName());
        e.setExecutorInn(x == null ? null : x.inn());
        e.setExecutorKpp(x == null ? null : x.kpp());
        e.setExecutorAddress(x == null ? null : x.address());
        e.setExecutorPhone(x == null ? null : x.phone());
        SiteSnapshot site = a.site();
        e.setSiteId(site == null ? null : site.id());
        e.setSiteName(site == null ? null : site.name());
        e.setSiteAddress(site == null ? null : site.address());
        e.setSiteCode(site == null ? null : site.code());
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
                        from act_participant p where p.act_id = w.id),
                    has_signed_scan = exists (select 1 from act_attachment x
                        where x.act_id = w.id and x.kind = 'SIGNED_ACT'),
                    search_vector =
                          setweight(to_tsvector('russian', concat_ws(' ', w.number, w.short_description)), 'A')
                       || setweight(to_tsvector('russian', concat_ws(' ', w.org_name, w.org_short_name,
                              w.department_name, w.site_name, w.equipment_type_name, w.equipment_brand,
                              w.equipment_model, w.equipment_serial, w.equipment_garage, w.equipment_plate)), 'B')
                       || setweight(to_tsvector('russian', concat_ws(' ', w.detailed_description, w.recommendations,
                              w.location, w.customer_rep_name)), 'C')
                       || setweight(to_tsvector('russian', concat_ws(' ',
                              (select string_agg(p.full_name, ' ') from act_participant p where p.act_id = w.id),
                              (select string_agg(concat_ws(' ', l.name, l.catalog_number), ' ')
                                 from act_part_line l where l.act_id = w.id),
                              (select string_agg(f.caption, ' ') from act_photo f where f.act_id = w.id))), 'D')
                where w.id = ?
                """, id);
    }

    /**
     * Реестр актов на SQL PostgreSQL: полнотекстовый поиск ({@code websearch_to_tsquery('russian', …)}, ранжирование
     * {@code ts_rank}, подсветка {@code ts_headline}) плюс поиск по части номеров техники, акта и каталожных
     * номеров запчастей — их пользователи вводят кусками. Фильтры добавляются только заданные.
     */
    @Override
    public PageResult<ActSummary> search(ActSearchQuery q, PageQuery page) {
        StringBuilder where = new StringBuilder(" where true ");
        MapSqlParameterSource params = new MapSqlParameterSource();
        String text = q.text() == null || q.text().isBlank() ? null : q.text().strip();
        if (text != null) {
            params.addValue("q", text);
            params.addValue("like", "%" + text.toLowerCase(java.util.Locale.ROOT)
                    .replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_") + "%");
            where.append("""
                     and (w.search_vector @@ websearch_to_tsquery('russian', :q)
                          or lower(coalesce(w.number, '')) like :like
                          or lower(coalesce(w.equipment_serial, '')) like :like
                          or lower(coalesce(w.equipment_garage, '')) like :like
                          or lower(coalesce(w.equipment_plate, '')) like :like
                          or exists (select 1 from act_part_line l where l.act_id = w.id
                                     and lower(coalesce(l.catalog_number, '')) like :like))""");
        }
        if (q.statuses() != null && !q.statuses().isEmpty()) {
            where.append(" and w.status in (:statuses)");
            params.addValue("statuses", q.statuses().stream().map(Enum::name).toList());
        }
        condition(where, params, "w.org_id = :organizationId", "organizationId", q.organizationId());
        condition(where, params, "w.equipment_id = :equipmentId", "equipmentId", q.equipmentId());
        condition(where, params, "w.site_id = :siteId", "siteId", q.siteId());
        condition(where, params, "w.short_description = :shortDescription", "shortDescription",
                q.shortDescription() == null || q.shortDescription().isBlank() ? null : q.shortDescription().strip());
        condition(where, params, "w.has_signed_scan = :signedScan", "signedScan", q.signedScan());
        condition(where, params, "exists (select 1 from act_participant p where p.act_id = w.id and p.engineer_id = :engineerId)",
                "engineerId", q.engineerId());
        condition(where, params, "exists (select 1 from act_participant p2 where p2.act_id = w.id and p2.keycloak_user_id = :userId)",
                "userId", q.participantUserId());
        if (q.partNumber() != null && !q.partNumber().isBlank()) {
            where.append(" and exists (select 1 from act_part_line l2 where l2.act_id = w.id"
                    + " and upper(coalesce(l2.catalog_number, '')) like :partNumber)");
            params.addValue("partNumber", "%" + q.partNumber().strip().toUpperCase(java.util.Locale.ROOT) + "%");
        }
        if (q.from() != null) {
            where.append(" and (w.period_end >= :from or (w.period_end is null and w.created_at >= :fromInstant))");
            params.addValue("from", q.from());
            params.addValue("fromInstant", java.sql.Timestamp.from(q.from().atStartOfDay(ZoneOffset.UTC).toInstant()));
        }
        if (q.to() != null) {
            where.append(" and (w.period_start <= :to or (w.period_start is null and w.created_at < :toInstant))");
            params.addValue("to", q.to());
            params.addValue("toInstant",
                    java.sql.Timestamp.from(q.to().plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant()));
        }

        Long total = jdbc().queryForObject("select count(*) from work_act w" + where, params, Long.class);
        String rank = text != null ? "ts_rank(w.search_vector, websearch_to_tsquery('russian', :q))" : "0";
        String headline = text != null
                ? "ts_headline('russian', concat_ws(' — ', w.short_description, w.detailed_description, w.recommendations),"
                        + " websearch_to_tsquery('russian', :q),"
                        + " 'StartSel=[[, StopSel=]], MaxWords=22, MinWords=8, MaxFragments=2, FragmentDelimiter= … ')"
                : "null";
        String order = page.sortField() != null
                ? SQL_SORT.orderBy(page.sortField(), page.ascending()).replace("e.id", "w.id")
                : text != null ? " order by rank desc, w.created_at desc, w.id asc" : " order by w.created_at desc, w.id asc";
        params.addValue("limit", page.size());
        params.addValue("offset", page.offset());
        List<ActSummary> items = jdbc().query("select w.*, " + rank + " as rank, " + headline + " as highlight"
                + " from work_act w" + where + order + " limit :limit offset :offset", params, (rs, n) -> summary(rs));
        return new PageResult<>(items, page.page(), page.size(), total == null ? 0 : total);
    }

    private static final SortWhitelist SQL_SORT = new SortWhitelist(Map.of(
            "number", "w.number",
            "createdAt", "w.created_at",
            "updatedAt", "w.updated_at",
            "periodStart", "w.period_start",
            "organizationName", "lower(coalesce(w.org_short_name, w.org_name))",
            "equipmentTitle", "lower(concat_ws(' ', w.equipment_brand, w.equipment_model))",
            "shortDescription", "lower(w.short_description)",
            "engineerNames", "lower(w.engineer_names)",
            "totalHours", "w.total_hours",
            "status", "w.status"), "w.created_at");

    private static void condition(StringBuilder where, MapSqlParameterSource params, String sql, String name,
                                  Object value) {
        if (value != null) {
            where.append(" and ").append(sql);
            params.addValue(name, value instanceof UUID ? value : value);
        }
    }

    private org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate jdbc() {
        return new org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate(jdbc);
    }

    private static ActSummary summary(java.sql.ResultSet rs) throws java.sql.SQLException {
        List<String> numbers = new ArrayList<>();
        if (rs.getString("equipment_garage") != null) {
            numbers.add("гар. № " + rs.getString("equipment_garage"));
        }
        if (rs.getString("equipment_plate") != null) {
            numbers.add(rs.getString("equipment_plate"));
        }
        if (numbers.isEmpty() && rs.getString("equipment_serial") != null) {
            numbers.add("S/N " + rs.getString("equipment_serial"));
        }
        String title = rs.getObject("equipment_id") == null ? null
                : rs.getString("equipment_brand") + " " + rs.getString("equipment_model");
        String org = rs.getString("org_short_name") != null ? rs.getString("org_short_name") : rs.getString("org_name");
        java.sql.Date start = rs.getDate("period_start");
        java.sql.Date end = rs.getDate("period_end");
        java.sql.Timestamp created = rs.getTimestamp("created_at");
        java.sql.Timestamp updated = rs.getTimestamp("updated_at");
        return new ActSummary(rs.getObject("id", UUID.class), rs.getString("number"),
                com.company.acts.act.domain.ActStatus.valueOf(rs.getString("status")), org,
                rs.getString("department_name"), title, String.join(" · ", numbers), rs.getString("short_description"),
                start == null ? null : start.toLocalDate(), end == null ? null : end.toLocalDate(),
                rs.getBigDecimal("total_hours"), rs.getString("engineer_names"),
                created == null ? null : created.toInstant(), updated == null ? null : updated.toInstant(),
                rs.getBoolean("has_signed_scan"), rs.getString("site_name"), rs.getString("highlight"),
                rs.getTimestamp("last_sent_at") == null ? null : rs.getTimestamp("last_sent_at").toInstant());
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
    public String next(int year, String siteCode) {
        String counter = siteCode == null ? numberPrefix : numberPrefix + "-" + siteCode;
        Long value = jdbc.queryForObject("""
                insert into act_number_sequence (year, prefix, last_value) values (?, ?, 1)
                on conflict (year, prefix) do update set last_value = act_number_sequence.last_value + 1
                returning last_value
                """, Long.class, year, counter);
        return ActNumberFormat.format(numberPrefix, siteCode, year, value == null ? 1 : value);
    }

    @Override
    public int deleteCancelledBefore(Instant before) {
        em.flush();
        return jdbc.update("delete from work_act where status = 'CANCELLED' and updated_at < ?",
                java.sql.Timestamp.from(before));
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
                e.getAuthorUserId(), e.getClosedAt(), e.getClosedBy(),
                e.getExecutorId() == null ? null : new ExecutorSnapshot(e.getExecutorId(), e.getExecutorName(),
                        e.getExecutorShortName(), e.getExecutorInn(), e.getExecutorKpp(), e.getExecutorAddress(),
                        e.getExecutorPhone()),
                e.getSiteId() == null ? null : new SiteSnapshot(e.getSiteId(), e.getSiteName(), e.getSiteCode(),
                        e.getSiteAddress()));
    }

}
