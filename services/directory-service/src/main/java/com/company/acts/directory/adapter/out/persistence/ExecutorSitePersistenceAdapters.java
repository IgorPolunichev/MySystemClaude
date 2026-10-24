package com.company.acts.directory.adapter.out.persistence;

import com.company.acts.common.core.ConflictException;
import com.company.acts.directory.application.port.out.ExecutorRepository;
import com.company.acts.directory.application.port.out.SiteRepository;
import com.company.acts.directory.domain.Executor;
import com.company.acts.directory.domain.Site;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Component;

/** Адаптеры исполнителей и площадок. */
final class ExecutorSitePersistenceAdapters {

    private ExecutorSitePersistenceAdapters() {
    }

    @Component
    static class Executors implements ExecutorRepository {

        private final ExecutorJpaRepository repository;

        Executors(ExecutorJpaRepository repository) {
            this.repository = repository;
        }

        @Override
        public Optional<Executor> findById(UUID id) {
            return repository.findById(id).filter(e -> !e.isDeleted()).map(Executors::toDomain);
        }

        @Override
        public Optional<Executor> findDefault() {
            return repository.findFirstByIsDefaultTrueAndDeletedAtIsNull().map(Executors::toDomain);
        }

        @Override
        public Optional<Executor> findByInn(String inn) {
            return repository.findFirstByInnAndDeletedAtIsNull(inn).map(Executors::toDomain);
        }

        @Override
        public List<Executor> findAll(boolean includeInactive) {
            return repository.findByDeletedAtIsNullOrderByNameAsc().stream()
                    .filter(e -> includeInactive || e.active).map(Executors::toDomain).toList();
        }

        @Override
        public Executor save(Executor x) {
            ExecutorJpaEntity e = repository.findById(x.id()).orElseGet(() -> {
                ExecutorJpaEntity created = new ExecutorJpaEntity();
                created.setId(x.id());
                return created;
            });
            if (x.version() != null && !Objects.equals(x.version(), e.getVersion())) {
                throw ConflictException.staleVersion();
            }
            e.name = x.name();
            e.shortName = x.shortName();
            e.inn = x.inn();
            e.kpp = x.kpp();
            e.ogrn = x.ogrn();
            e.address = x.address();
            e.phone = x.phone();
            e.email = x.email();
            e.bankDetails = x.bankDetails();
            e.isDefault = x.isDefault();
            e.active = x.active();
            return toDomain(repository.saveAndFlush(e));
        }

        static Executor toDomain(ExecutorJpaEntity e) {
            return new Executor(e.getId(), e.getVersion(), e.name, e.shortName, e.inn, e.kpp, e.ogrn, e.address,
                    e.phone, e.email, e.bankDetails, e.isDefault, e.active);
        }
    }

    @Component
    static class Sites implements SiteRepository {

        private final SiteJpaRepository repository;

        Sites(SiteJpaRepository repository) {
            this.repository = repository;
        }

        @Override
        public Optional<Site> findById(UUID id) {
            return repository.findById(id).filter(e -> !e.isDeleted()).map(Sites::toDomain);
        }

        @Override
        public List<Site> findByOrganization(UUID organizationId, boolean includeInactive) {
            return repository.findByOrganizationIdAndDeletedAtIsNullOrderByNameAsc(organizationId).stream()
                    .filter(e -> includeInactive || e.active).map(Sites::toDomain).toList();
        }

        @Override
        public Site save(Site s) {
            SiteJpaEntity e = repository.findById(s.id()).orElseGet(() -> {
                SiteJpaEntity created = new SiteJpaEntity();
                created.setId(s.id());
                created.organizationId = s.organizationId();
                return created;
            });
            if (s.version() != null && !Objects.equals(s.version(), e.getVersion())) {
                throw ConflictException.staleVersion();
            }
            e.name = s.name();
            e.code = s.code();
            e.address = s.address();
            e.active = s.active();
            return toDomain(repository.saveAndFlush(e));
        }

        static Site toDomain(SiteJpaEntity e) {
            return new Site(e.getId(), e.getVersion(), e.organizationId, e.name, e.code, e.address, e.active);
        }
    }
}
