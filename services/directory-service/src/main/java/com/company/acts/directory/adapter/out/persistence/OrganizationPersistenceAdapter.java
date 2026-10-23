package com.company.acts.directory.adapter.out.persistence;

import com.company.acts.common.core.ConflictException;
import com.company.acts.common.core.PageQuery;
import com.company.acts.common.core.PageResult;
import com.company.acts.common.persistence.JpqlFilter;
import com.company.acts.common.persistence.SortWhitelist;
import com.company.acts.directory.application.port.out.OrganizationRepository;
import com.company.acts.directory.domain.Organization;
import jakarta.persistence.EntityManager;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
class OrganizationPersistenceAdapter implements OrganizationRepository {

    private static final SortWhitelist SORT = new SortWhitelist(Map.of(
            "name", "lower(e.name)", "shortName", "lower(e.shortName)", "inn", "e.inn",
            "updatedAt", "e.updatedAt"), "lower(e.name)");

    private final OrganizationsJpaRepository repository;
    private final EntityManager em;

    OrganizationPersistenceAdapter(OrganizationsJpaRepository repository, EntityManager em) {
        this.repository = repository;
        this.em = em;
    }

    @Override
    public Optional<Organization> findById(UUID id) {
        return repository.findByIdAndDeletedAtIsNull(id).map(OrganizationPersistenceAdapter::toDomain);
    }

    @Override
    public Optional<Organization> findByInn(String inn) {
        return repository.findFirstByInnAndDeletedAtIsNull(inn).map(OrganizationPersistenceAdapter::toDomain);
    }

    @Override
    public Optional<Organization> findByNameIgnoreCase(String name) {
        if (name == null) {
            return Optional.empty();
        }
        String key = name.strip();
        return repository.findFirstByNameIgnoreCaseAndDeletedAtIsNull(key)
                .or(() -> repository.findFirstByShortNameIgnoreCaseAndDeletedAtIsNull(key))
                .map(OrganizationPersistenceAdapter::toDomain);
    }

    @Override
    public PageResult<Organization> search(String text, Boolean active, PageQuery page) {
        JpqlFilter filter = new JpqlFilter()
                .where("e.deletedAt is null")
                .where("e.active = :active", "active", active)
                .search(text, "e.name", "coalesce(e.shortName, '')", "coalesce(e.inn, '')");
        long total = filter.query(em, "select count(e) from OrganizationJpaEntity e" + filter.clause(), Long.class)
                .getSingleResult();
        var items = filter.query(em, "select e from OrganizationJpaEntity e" + filter.clause()
                        + SORT.orderBy(page.sortField(), page.ascending()), OrganizationJpaEntity.class)
                .setFirstResult(page.offset())
                .setMaxResults(page.size())
                .getResultList()
                .stream().map(OrganizationPersistenceAdapter::toDomain).toList();
        return new PageResult<>(items, page.page(), page.size(), total);
    }

    @Override
    public Organization save(Organization o) {
        OrganizationJpaEntity entity = repository.findById(o.id()).orElseGet(() -> {
            OrganizationJpaEntity created = new OrganizationJpaEntity();
            created.setId(o.id());
            return created;
        });
        if (o.version() != null && !Objects.equals(o.version(), entity.getVersion())) {
            throw ConflictException.staleVersion();
        }
        entity.setName(o.name());
        entity.setShortName(o.shortName());
        entity.setInn(o.inn());
        entity.setKpp(o.kpp());
        entity.setOgrn(o.ogrn());
        entity.setLegalAddress(o.legalAddress());
        entity.setActualAddress(o.actualAddress());
        entity.setBankDetails(o.bankDetails());
        entity.setPhone(o.phone());
        entity.setEmail(o.email());
        entity.setNotes(o.notes());
        entity.setActive(o.active());
        return toDomain(repository.saveAndFlush(entity));
    }

    @Override
    public void delete(UUID id) {
        repository.findByIdAndDeletedAtIsNull(id).ifPresent(e -> {
            e.markDeleted();
            repository.saveAndFlush(e);
        });
    }

    static Organization toDomain(OrganizationJpaEntity e) {
        return new Organization(e.getId(), e.getVersion(), e.getName(), e.getShortName(), e.getInn(), e.getKpp(),
                e.getOgrn(), e.getLegalAddress(), e.getActualAddress(), e.getBankDetails(), e.getPhone(), e.getEmail(),
                e.getNotes(), e.isActive());
    }
}
