package com.company.acts.act.adapter.out.persistence;

import com.company.acts.act.application.port.out.PartLineRepository;
import com.company.acts.act.domain.PartLine;
import com.company.acts.act.domain.UnitOfMeasure;
import jakarta.persistence.EntityManager;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
class PartLinePersistenceAdapter implements PartLineRepository {

    private final PartLineJpaRepository repository;
    private final EntityManager em;

    PartLinePersistenceAdapter(PartLineJpaRepository repository, EntityManager em) {
        this.repository = repository;
        this.em = em;
    }

    @Override
    public List<PartLine> findByAct(UUID actId) {
        return repository.findByActIdOrderByPositionAsc(actId).stream().map(PartLinePersistenceAdapter::toDomain)
                .toList();
    }

    @Override
    public Optional<PartLine> findById(UUID id) {
        return repository.findById(id).map(PartLinePersistenceAdapter::toDomain);
    }

    @Override
    public int nextPosition(UUID actId) {
        Integer max = em.createQuery("select max(l.position) from PartLineJpaEntity l where l.actId = :actId",
                        Integer.class)
                .setParameter("actId", actId)
                .getSingleResult();
        return max == null ? 1 : max + 1;
    }

    @Override
    public PartLine save(PartLine l) {
        PartLineJpaEntity e = repository.findById(l.id()).orElseGet(() -> {
            PartLineJpaEntity created = new PartLineJpaEntity();
            created.setId(l.id());
            created.setActId(l.actId());
            return created;
        });
        e.setPosition(l.position());
        e.setSource(l.source());
        e.setCatalogItemId(l.catalogItemId());
        e.setName(l.name());
        e.setCatalogNumber(l.catalogNumber());
        e.setQuantity(l.quantity());
        e.setUnitCode(l.unit().name());
        e.setNotes(l.notes());
        return toDomain(repository.saveAndFlush(e));
    }

    @Override
    public void delete(UUID id) {
        repository.findById(id).ifPresent(e -> {
            repository.delete(e);
            repository.flush();
        });
    }

    private static PartLine toDomain(PartLineJpaEntity e) {
        return new PartLine(e.getId(), e.getActId(), e.getPosition(), e.getSource(), e.getCatalogItemId(),
                e.getName(), e.getCatalogNumber(), e.getQuantity(), UnitOfMeasure.of(e.getUnitCode()), e.getNotes());
    }
}
