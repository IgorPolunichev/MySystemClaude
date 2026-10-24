package com.company.acts.directory.application.port.out;

import com.company.acts.directory.domain.Executor;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ExecutorRepository {

    Optional<Executor> findById(UUID id);

    Optional<Executor> findDefault();

    Optional<Executor> findByInn(String inn);

    List<Executor> findAll(boolean includeInactive);

    Executor save(Executor executor);
}
