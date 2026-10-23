package com.company.acts.act.adapter.out.persistence;

import com.company.acts.act.application.port.out.PhotoRepository;
import com.company.acts.act.domain.ActPhoto;
import jakarta.persistence.EntityManager;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
class PhotoPersistenceAdapter implements PhotoRepository {

    private final PhotoJpaRepository repository;
    private final EntityManager em;

    PhotoPersistenceAdapter(PhotoJpaRepository repository, EntityManager em) {
        this.repository = repository;
        this.em = em;
    }

    @Override
    public List<ActPhoto> findByAct(UUID actId) {
        return repository.findByActIdOrderByPositionAsc(actId).stream().map(PhotoPersistenceAdapter::toDomain).toList();
    }

    @Override
    public Optional<ActPhoto> findById(UUID id) {
        return repository.findById(id).map(PhotoPersistenceAdapter::toDomain);
    }

    @Override
    public int nextPosition(UUID actId) {
        Integer max = em.createQuery("select max(p.position) from PhotoJpaEntity p where p.actId = :actId", Integer.class)
                .setParameter("actId", actId)
                .getSingleResult();
        return max == null ? 1 : max + 1;
    }

    @Override
    public ActPhoto save(ActPhoto p) {
        PhotoJpaEntity e = repository.findById(p.id()).orElseGet(() -> {
            PhotoJpaEntity created = new PhotoJpaEntity();
            created.setId(p.id());
            created.setActId(p.actId());
            created.setMediaId(p.mediaId());
            return created;
        });
        e.setCaption(p.caption());
        e.setPosition(p.position());
        return toDomain(repository.saveAndFlush(e));
    }

    @Override
    public void delete(UUID id) {
        repository.findById(id).ifPresent(e -> {
            repository.delete(e);
            repository.flush();
        });
    }

    private static ActPhoto toDomain(PhotoJpaEntity e) {
        return new ActPhoto(e.getId(), e.getActId(), e.getMediaId(), e.getCaption(), e.getPosition());
    }
}
