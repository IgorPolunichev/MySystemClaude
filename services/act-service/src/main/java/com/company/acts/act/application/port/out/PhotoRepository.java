package com.company.acts.act.application.port.out;

import com.company.acts.act.domain.ActPhoto;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PhotoRepository {

    List<ActPhoto> findByAct(UUID actId);

    Optional<ActPhoto> findById(UUID id);

    int nextPosition(UUID actId);

    ActPhoto save(ActPhoto photo);

    void delete(UUID id);
}
