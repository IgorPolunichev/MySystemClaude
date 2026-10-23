package com.company.acts.act.application.port.out;

import com.company.acts.act.domain.PartLine;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PartLineRepository {

    List<PartLine> findByAct(UUID actId);

    Optional<PartLine> findById(UUID id);

    int nextPosition(UUID actId);

    PartLine save(PartLine line);

    void delete(UUID id);
}
