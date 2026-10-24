package com.company.acts.act.adapter.out.persistence;

import com.company.acts.act.application.port.out.AttachmentRepository;
import com.company.acts.act.domain.ActAttachment;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
class AttachmentPersistenceAdapter implements AttachmentRepository {

    private final AttachmentJpaRepository repository;

    AttachmentPersistenceAdapter(AttachmentJpaRepository repository) {
        this.repository = repository;
    }

    @Override
    public List<ActAttachment> findByAct(UUID actId) {
        return repository.findByActIdOrderByUploadedAtAsc(actId).stream().map(AttachmentPersistenceAdapter::toDomain)
                .toList();
    }

    @Override
    public Optional<ActAttachment> findById(UUID id) {
        return repository.findById(id).map(AttachmentPersistenceAdapter::toDomain);
    }

    @Override
    public ActAttachment save(ActAttachment a) {
        AttachmentJpaEntity e = new AttachmentJpaEntity();
        e.id = a.id();
        e.actId = a.actId();
        e.mediaId = a.mediaId();
        e.kind = a.kind();
        e.fileName = a.fileName();
        e.contentType = a.contentType();
        e.sizeBytes = a.sizeBytes();
        e.comment = a.comment();
        e.uploadedBy = a.uploadedBy();
        e.uploadedAt = a.uploadedAt();
        return toDomain(repository.saveAndFlush(e));
    }

    @Override
    public void delete(UUID id) {
        repository.findById(id).ifPresent(e -> {
            repository.delete(e);
            repository.flush();
        });
    }

    private static ActAttachment toDomain(AttachmentJpaEntity e) {
        return new ActAttachment(e.id, e.actId, e.mediaId, e.kind, e.fileName, e.contentType, e.sizeBytes, e.comment,
                e.uploadedBy, e.uploadedAt);
    }
}
