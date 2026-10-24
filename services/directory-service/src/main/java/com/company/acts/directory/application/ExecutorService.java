package com.company.acts.directory.application;

import com.company.acts.common.core.ConflictException;
import com.company.acts.common.core.NotFoundException;
import com.company.acts.common.core.Uuid7;
import com.company.acts.directory.application.command.ExecutorCommand;
import com.company.acts.directory.application.port.out.ExecutorRepository;
import com.company.acts.directory.domain.Executor;
import java.util.List;
import java.util.UUID;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Исполнители (ваши компании). Список видят все, изменяет администратор. Основной исполнитель всегда один:
 * назначение нового снимает отметку с прежнего.
 */
@Service
public class ExecutorService {

    private final ExecutorRepository executors;

    public ExecutorService(ExecutorRepository executors) {
        this.executors = executors;
    }

    @Transactional(readOnly = true)
    public List<Executor> list(boolean includeInactive) {
        return executors.findAll(includeInactive);
    }

    @Transactional(readOnly = true)
    public Executor get(UUID id) {
        return executors.findById(id).orElseThrow(() -> NotFoundException.of("Исполнитель", id));
    }

    @Transactional(readOnly = true)
    public Executor getDefault() {
        return executors.findDefault().orElseThrow(() -> new NotFoundException("Основной исполнитель не назначен"));
    }

    @PreAuthorize(Roles.ADMIN)
    @Transactional
    public Executor create(ExecutorCommand c) {
        if (c.id() != null) {
            var existing = executors.findById(c.id());
            if (existing.isPresent()) {
                return existing.get();
            }
        }
        boolean first = executors.findDefault().isEmpty();
        Executor executor = toDomain(c.id() != null ? c.id() : Uuid7.next(), null, c, first);
        return store(executor);
    }

    @PreAuthorize(Roles.ADMIN)
    @Transactional
    public Executor update(UUID id, ExecutorCommand c) {
        Executor current = get(id);
        Versions.check(c.version(), current.version());
        if (current.isDefault() && Boolean.FALSE.equals(c.isDefault())) {
            throw new ConflictException("Нельзя снять отметку «основной» — назначьте основным другого исполнителя");
        }
        return store(toDomain(id, current.version(), c, current.isDefault()));
    }

    private Executor store(Executor executor) {
        if (executor.inn() != null) {
            executors.findByInn(executor.inn()).filter(o -> !o.id().equals(executor.id())).ifPresent(o -> {
                throw new ConflictException("Исполнитель с ИНН " + executor.inn() + " уже есть: «" + o.name() + "»");
            });
        }
        if (executor.isDefault()) {
            executors.findDefault().filter(o -> !o.id().equals(executor.id())).ifPresent(o -> executors.save(
                    new Executor(o.id(), o.version(), o.name(), o.shortName(), o.inn(), o.kpp(), o.ogrn(), o.address(),
                            o.phone(), o.email(), o.bankDetails(), false, o.active())));
        }
        return executors.save(executor);
    }

    private static Executor toDomain(UUID id, Long version, ExecutorCommand c, boolean defaultIfUnset) {
        boolean isDefault = c.isDefault() != null ? c.isDefault() : defaultIfUnset;
        return new Executor(id, version, c.name(), c.shortName(), c.inn(), c.kpp(), c.ogrn(), c.address(), c.phone(),
                c.email(), c.bankDetails(), isDefault, c.active() == null || c.active());
    }
}
