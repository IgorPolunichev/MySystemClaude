package com.company.acts.parts.adapter.out.persistence;

import com.company.acts.common.core.ConflictException;
import com.company.acts.common.core.PageQuery;
import com.company.acts.common.core.PageResult;
import com.company.acts.common.persistence.JpqlFilter;
import com.company.acts.common.persistence.SortWhitelist;
import com.company.acts.parts.application.port.out.PartRepository;
import com.company.acts.parts.application.port.out.UnitRepository;
import com.company.acts.parts.domain.Part;
import com.company.acts.parts.domain.Unit;
import jakarta.persistence.EntityManager;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
class PartPersistenceAdapter implements PartRepository, UnitRepository {

    private static final SortWhitelist SORT = new SortWhitelist(Map.of(
            "name", "lower(e.name)", "catalogNumber", "e.catalogNumber",
            "manufacturer", "lower(e.manufacturer)", "updatedAt", "e.updatedAt"), "lower(e.name)");

    private final PartsJpaRepository parts;
    private final UnitsJpaRepository units;
    private final EntityManager em;

    PartPersistenceAdapter(PartsJpaRepository parts, UnitsJpaRepository units, EntityManager em) {
        this.parts = parts;
        this.units = units;
        this.em = em;
    }

    @Override
    public Optional<Part> findById(UUID id) {
        return parts.findByIdAndDeletedAtIsNull(id).map(PartPersistenceAdapter::toDomain);
    }

    /** Номер сравнивается точно (он нормализован), производитель — без учёта регистра; пустой производитель = пустой. */
    @Override
    public Optional<Part> findByCatalogNumber(String catalogNumber, String manufacturer) {
        JpqlFilter filter = new JpqlFilter()
                .where("e.deletedAt is null")
                .where("e.catalogNumber = :number", "number", catalogNumber)
                .where("lower(coalesce(e.manufacturer, '')) = :manufacturer", "manufacturer",
                        manufacturer == null ? "" : manufacturer.strip().toLowerCase(java.util.Locale.ROOT));
        return filter.query(em, "select e from PartJpaEntity e" + filter.clause(), PartJpaEntity.class)
                .setMaxResults(1).getResultStream().findFirst().map(PartPersistenceAdapter::toDomain);
    }

    @Override
    public Optional<Part> findByNameIgnoreCase(String name) {
        return name == null ? Optional.empty()
                : parts.findFirstByNameIgnoreCaseAndDeletedAtIsNull(name.strip()).map(PartPersistenceAdapter::toDomain);
    }

    @Override
    public PageResult<Part> search(String text, PageQuery page) {
        JpqlFilter filter = new JpqlFilter()
                .where("e.deletedAt is null")
                .search(text, "e.name", "coalesce(e.catalogNumber, '')", "coalesce(e.manufacturer, '')");
        long total = filter.query(em, "select count(e) from PartJpaEntity e" + filter.clause(), Long.class)
                .getSingleResult();
        List<Part> items = filter.query(em, "select e from PartJpaEntity e" + filter.clause()
                        + SORT.orderBy(page.sortField(), page.ascending()), PartJpaEntity.class)
                .setFirstResult(page.offset())
                .setMaxResults(page.size())
                .getResultList()
                .stream().map(PartPersistenceAdapter::toDomain).toList();
        return new PageResult<>(items, page.page(), page.size(), total);
    }

    @Override
    public Part save(Part p) {
        PartJpaEntity entity = parts.findById(p.id()).orElseGet(() -> {
            PartJpaEntity created = new PartJpaEntity();
            created.setId(p.id());
            return created;
        });
        if (p.version() != null && !Objects.equals(p.version(), entity.getVersion())) {
            throw ConflictException.staleVersion();
        }
        entity.setName(p.name());
        entity.setCatalogNumber(p.catalogNumber());
        entity.setManufacturer(p.manufacturer());
        entity.setDefaultUnit(p.defaultUnit());
        entity.setFluid(p.fluid());
        entity.setNotes(p.notes());
        return toDomain(parts.saveAndFlush(entity));
    }

    @Override
    public void delete(UUID id) {
        parts.findByIdAndDeletedAtIsNull(id).ifPresent(e -> {
            e.markDeleted();
            parts.saveAndFlush(e);
        });
    }

    @Override
    public List<Unit> findAll() {
        return units.findAllByOrderBySortOrderAsc().stream()
                .map(u -> new Unit(u.getCode(), u.getName(), u.isAllowsFraction())).toList();
    }

    @Override
    public Optional<Unit> findByCode(String code) {
        return units.findById(code).map(u -> new Unit(u.getCode(), u.getName(), u.isAllowsFraction()));
    }

    static Part toDomain(PartJpaEntity e) {
        return new Part(e.getId(), e.getVersion(), e.getName(), e.getCatalogNumber(), e.getManufacturer(),
                e.getDefaultUnit(), e.isFluid(), e.getNotes());
    }
}
