package com.company.acts.directory.adapter.out.persistence;

import com.company.acts.common.core.ConflictException;
import com.company.acts.common.core.PageQuery;
import com.company.acts.common.core.PageResult;
import com.company.acts.common.persistence.JpqlFilter;
import com.company.acts.common.persistence.SortWhitelist;
import com.company.acts.directory.application.port.out.EquipmentRepository;
import com.company.acts.directory.application.view.EquipmentView;
import com.company.acts.directory.domain.Equipment;
import jakarta.persistence.EntityManager;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
class EquipmentPersistenceAdapter implements EquipmentRepository {

    /** Техника с названиями организации, подразделения и типа одним запросом. */
    private static final String VIEW_FROM = " from EquipmentJpaEntity e"
            + " join OrganizationJpaEntity o on o.id = e.organizationId"
            + " join EquipmentTypeJpaEntity t on t.id = e.equipmentTypeId"
            + " left join DepartmentJpaEntity d on d.id = e.departmentId";

    private static final SortWhitelist SORT = new SortWhitelist(Map.of(
            "organizationName", "lower(coalesce(o.shortName, o.name))",
            "departmentName", "lower(d.name)",
            "equipmentTypeName", "lower(t.name)",
            "brand", "lower(e.brand)",
            "model", "lower(e.model)",
            "serialNumber", "e.serialNumber",
            "garageNumber", "e.garageNumber",
            "plateNumber", "e.plateNumber",
            "updatedAt", "e.updatedAt"), "lower(e.brand)");

    private final EquipmentJpaRepository repository;
    private final EntityManager em;

    EquipmentPersistenceAdapter(EquipmentJpaRepository repository, EntityManager em) {
        this.repository = repository;
        this.em = em;
    }

    @Override
    public Optional<Equipment> findById(UUID id) {
        return repository.findByIdAndDeletedAtIsNull(id).map(EquipmentPersistenceAdapter::toDomain);
    }

    @Override
    public Optional<EquipmentView> findViewById(UUID id) {
        JpqlFilter filter = new JpqlFilter().where("e.deletedAt is null").where("e.id = :id", "id", id);
        return filter.query(em, "select e, coalesce(o.shortName, o.name), d.name, t.name" + VIEW_FROM + filter.clause(),
                        Object[].class)
                .getResultList().stream().findFirst().map(EquipmentPersistenceAdapter::toView);
    }

    @Override
    public Optional<Equipment> findBySerial(UUID organizationId, String serialNumber) {
        return repository.findFirstByOrganizationIdAndSerialNumberAndDeletedAtIsNull(organizationId, serialNumber)
                .map(EquipmentPersistenceAdapter::toDomain);
    }

    @Override
    public Optional<Equipment> findByGarageNumber(UUID organizationId, String garageNumber) {
        return repository.findFirstByOrganizationIdAndGarageNumberAndDeletedAtIsNull(organizationId, garageNumber)
                .map(EquipmentPersistenceAdapter::toDomain);
    }

    @Override
    public Optional<Equipment> findByPlateNumber(UUID organizationId, String plateNumber) {
        return repository.findFirstByOrganizationIdAndPlateNumberAndDeletedAtIsNull(organizationId, plateNumber)
                .map(EquipmentPersistenceAdapter::toDomain);
    }

    @Override
    public PageResult<EquipmentView> search(String text, UUID organizationId, Collection<UUID> departmentIds,
                                            UUID equipmentTypeId, PageQuery page) {
        JpqlFilter filter = new JpqlFilter()
                .where("e.deletedAt is null")
                .where("e.organizationId = :organizationId", "organizationId", organizationId)
                .where("e.departmentId in :departmentIds", "departmentIds", departmentIds)
                .where("e.equipmentTypeId = :typeId", "typeId", equipmentTypeId)
                .search(text, "coalesce(e.serialNumber, '')", "coalesce(e.garageNumber, '')",
                        "coalesce(e.plateNumber, '')", "e.brand", "e.model", "concat(e.brand, ' ', e.model)");
        long total = filter.query(em, "select count(e)" + VIEW_FROM + filter.clause(), Long.class).getSingleResult();
        List<EquipmentView> items = filter.query(em, "select e, coalesce(o.shortName, o.name), d.name, t.name"
                        + VIEW_FROM + filter.clause() + SORT.orderBy(page.sortField(), page.ascending()), Object[].class)
                .setFirstResult(page.offset())
                .setMaxResults(page.size())
                .getResultList()
                .stream().map(EquipmentPersistenceAdapter::toView).toList();
        return new PageResult<>(items, page.page(), page.size(), total);
    }

    @Override
    public boolean existsByOrganization(UUID organizationId) {
        return repository.existsByOrganizationIdAndDeletedAtIsNull(organizationId);
    }

    @Override
    public boolean existsByDepartment(UUID departmentId) {
        return repository.existsByDepartmentIdAndDeletedAtIsNull(departmentId);
    }

    @Override
    public boolean existsByType(UUID equipmentTypeId) {
        return repository.existsByEquipmentTypeIdAndDeletedAtIsNull(equipmentTypeId);
    }

    @Override
    public Equipment save(Equipment q) {
        EquipmentJpaEntity entity = repository.findById(q.id()).orElseGet(() -> {
            EquipmentJpaEntity created = new EquipmentJpaEntity();
            created.setId(q.id());
            return created;
        });
        if (q.version() != null && !Objects.equals(q.version(), entity.getVersion())) {
            throw ConflictException.staleVersion();
        }
        entity.setOrganizationId(q.organizationId());
        entity.setDepartmentId(q.departmentId());
        entity.setEquipmentTypeId(q.equipmentTypeId());
        entity.setBrand(q.brand());
        entity.setModel(q.model());
        entity.setSerialNumber(q.serialNumber());
        entity.setGarageNumber(q.garageNumber());
        entity.setPlateNumber(q.plateNumber());
        entity.setMeterType(q.meterType());
        entity.setCurrentHours(q.currentHours());
        entity.setCurrentKm(q.currentKm());
        entity.setYear(q.year());
        entity.setNotes(q.notes());
        return toDomain(repository.saveAndFlush(entity));
    }

    @Override
    public void delete(UUID id) {
        repository.findByIdAndDeletedAtIsNull(id).ifPresent(e -> {
            e.markDeleted();
            repository.saveAndFlush(e);
        });
    }

    static Equipment toDomain(EquipmentJpaEntity e) {
        return new Equipment(e.getId(), e.getVersion(), e.getOrganizationId(), e.getDepartmentId(),
                e.getEquipmentTypeId(), e.getBrand(), e.getModel(), e.getSerialNumber(), e.getGarageNumber(),
                e.getPlateNumber(), e.getMeterType(), e.getCurrentHours(), e.getCurrentKm(), e.getYear(), e.getNotes());
    }

    private static EquipmentView toView(Object[] row) {
        EquipmentJpaEntity e = (EquipmentJpaEntity) row[0];
        return new EquipmentView(e.getId(), e.getOrganizationId(), (String) row[1], e.getDepartmentId(),
                (String) row[2], e.getEquipmentTypeId(), (String) row[3], e.getBrand(), e.getModel(),
                e.getSerialNumber(), e.getGarageNumber(), e.getPlateNumber(), e.getMeterType(), e.getCurrentHours(),
                e.getCurrentKm(), e.getYear(), e.getNotes(), e.getVersion(), e.getCreatedAt(), e.getUpdatedAt());
    }
}
