package com.company.acts.act.application.port.out;

import com.company.acts.act.domain.ActAttachment;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AttachmentRepository {

    List<ActAttachment> findByAct(UUID actId);

    Optional<ActAttachment> findById(UUID id);

    ActAttachment save(ActAttachment attachment);

    void delete(UUID id);
}
