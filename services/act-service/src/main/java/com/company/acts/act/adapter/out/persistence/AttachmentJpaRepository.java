package com.company.acts.act.adapter.out.persistence;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AttachmentJpaRepository extends JpaRepository<AttachmentJpaEntity, UUID> {

    List<AttachmentJpaEntity> findByActIdOrderByUploadedAtAsc(UUID actId);
}
