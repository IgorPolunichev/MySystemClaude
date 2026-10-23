package com.company.acts.directory.adapter.out.persistence;

import com.company.acts.common.core.ConflictException;
import com.company.acts.common.core.PageQuery;
import com.company.acts.common.core.PageResult;
import com.company.acts.common.persistence.JpqlFilter;
import com.company.acts.common.persistence.SortWhitelist;
import com.company.acts.directory.application.port.out.EngineerRepository;
import com.company.acts.directory.application.view.EngineerView;
import com.company.acts.directory.domain.Engineer;
import jakarta.persistence.EntityManager;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
class EngineerPersistenceAdapter implements EngineerRepository {

    private static final String VIEW_FROM = " from EngineerJpaEntity e"
            + " left join ServiceDepartmentJpaEntity s on s.id = e.serviceDepartmentId";

    private static final SortWhitelist SORT = new SortWhitelist(Map.of(
            "lastName", "lower(e.lastName)", "username", "e.username",
            "serviceDepartmentName", "lower(s.name)", "position", "lower(e.position)"), "lower(e.lastName)");

    private final EngineersJpaRepository repository;
    private final EntityManager em;

    EngineerPersistenceAdapter(EngineersJpaRepository repository, EntityManager em) {
        this.repository = repository;
        this.em = em;
    }

    @Override
    public Optional<Engineer> findById(UUID id) {
        return repository.findByIdAndDeletedAtIsNull(id).map(EngineerPersistenceAdapter::toDomain);
    }

    @Override
    public Optional<EngineerView> findViewById(UUID id) {
        JpqlFilter filter = new JpqlFilter().where("e.deletedAt is null").where("e.id = :id", "id", id);
        return filter.query(em, "select e, s.name" + VIEW_FROM + filter.clause(), Object[].class)
                .getResultStream().findFirst().map(EngineerPersistenceAdapter::toView);
    }

    @Override
    public Optional<Engineer> findByUsername(String username) {
        return repository.findFirstByUsernameAndDeletedAtIsNull(username).map(EngineerPersistenceAdapter::toDomain);
    }

    @Override
    public Optional<Engineer> findByKeycloakUserId(String keycloakUserId) {
        return repository.findFirstByKeycloakUserIdAndDeletedAtIsNull(keycloakUserId)
                .map(EngineerPersistenceAdapter::toDomain);
    }

    @Override
    public PageResult<EngineerView> search(String text, UUID serviceDepartmentId, Boolean active, PageQuery page) {
        JpqlFilter filter = new JpqlFilter()
                .where("e.deletedAt is null")
                .where("e.serviceDepartmentId = :sd", "sd", serviceDepartmentId)
                .where("e.active = :active", "active", active)
                .search(text, "e.lastName", "e.firstName", "coalesce(e.middleName, '')", "e.username");
        long total = filter.query(em, "select count(e)" + VIEW_FROM + filter.clause(), Long.class).getSingleResult();
        List<EngineerView> items = filter.query(em, "select e, s.name" + VIEW_FROM + filter.clause()
                        + SORT.orderBy(page.sortField(), page.ascending()), Object[].class)
                .setFirstResult(page.offset())
                .setMaxResults(page.size())
                .getResultList()
                .stream().map(EngineerPersistenceAdapter::toView).toList();
        return new PageResult<>(items, page.page(), page.size(), total);
    }

    @Override
    public boolean existsByServiceDepartment(UUID serviceDepartmentId) {
        return repository.existsByServiceDepartmentIdAndDeletedAtIsNull(serviceDepartmentId);
    }

    @Override
    public Engineer save(Engineer g) {
        EngineerJpaEntity entity = repository.findById(g.id()).orElseGet(() -> {
            EngineerJpaEntity created = new EngineerJpaEntity();
            created.setId(g.id());
            return created;
        });
        if (g.version() != null && !Objects.equals(g.version(), entity.getVersion())) {
            throw ConflictException.staleVersion();
        }
        entity.setKeycloakUserId(g.keycloakUserId());
        entity.setUsername(g.username());
        entity.setLastName(g.lastName());
        entity.setFirstName(g.firstName());
        entity.setMiddleName(g.middleName());
        entity.setPosition(g.position());
        entity.setServiceDepartmentId(g.serviceDepartmentId());
        entity.setPhone(g.phone());
        entity.setEmail(g.email());
        entity.setActive(g.active());
        return toDomain(repository.saveAndFlush(entity));
    }

    static Engineer toDomain(EngineerJpaEntity e) {
        return new Engineer(e.getId(), e.getVersion(), e.getKeycloakUserId(), e.getUsername(), e.getLastName(),
                e.getFirstName(), e.getMiddleName(), e.getPosition(), e.getServiceDepartmentId(), e.getPhone(),
                e.getEmail(), e.isActive());
    }

    private static EngineerView toView(Object[] row) {
        EngineerJpaEntity e = (EngineerJpaEntity) row[0];
        Engineer d = toDomain(e);
        return new EngineerView(e.getId(), e.getKeycloakUserId(), e.getUsername(), e.getLastName(), e.getFirstName(),
                e.getMiddleName(), d.fullName(), e.getPosition(), e.getServiceDepartmentId(), (String) row[1],
                e.getPhone(), e.getEmail(), e.isActive(), e.getVersion(), e.getCreatedAt(), e.getUpdatedAt());
    }
}
