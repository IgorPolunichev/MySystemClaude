package com.company.acts.directory.application;

import com.company.acts.common.core.BusinessRuleException;
import com.company.acts.common.core.ConflictException;
import com.company.acts.common.core.NotFoundException;
import com.company.acts.common.core.Uuid7;
import com.company.acts.directory.application.command.SiteCommand;
import com.company.acts.directory.application.port.out.OrganizationRepository;
import com.company.acts.directory.application.port.out.SiteRepository;
import com.company.acts.directory.domain.Site;
import java.util.List;
import java.util.UUID;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Производственные площадки заказчика. Площадки не удаляются, а блокируются — заблокированную нельзя
 * выбрать для техники и новых актов, но в уже выданных актах она остаётся.
 */
@Service
public class SiteService {

    private final SiteRepository sites;
    private final OrganizationRepository organizations;

    public SiteService(SiteRepository sites, OrganizationRepository organizations) {
        this.sites = sites;
        this.organizations = organizations;
    }

    @Transactional(readOnly = true)
    public List<Site> listByOrganization(UUID organizationId, boolean includeInactive) {
        organizations.findById(organizationId).orElseThrow(() -> NotFoundException.of("Организация", organizationId));
        return sites.findByOrganization(organizationId, includeInactive);
    }

    @Transactional(readOnly = true)
    public Site get(UUID id) {
        return sites.findById(id).orElseThrow(() -> NotFoundException.of("Площадка", id));
    }

    @Transactional
    public Site create(SiteCommand c) {
        if (c.id() != null) {
            var existing = sites.findById(c.id());
            if (existing.isPresent()) {
                return existing.get();
            }
        }
        organizations.findById(c.organizationId())
                .orElseThrow(() -> new BusinessRuleException("organizationId", "Организация не найдена"));
        Site site = new Site(c.id() != null ? c.id() : Uuid7.next(), null, c.organizationId(), c.name(), c.code(),
                c.address(),
                c.active() == null || c.active());
        ensureNameIsFree(site);
        return sites.save(site);
    }

    /** Переименовать, сменить адрес, заблокировать или разблокировать. */
    @PreAuthorize(Roles.CAN_EDIT)
    @Transactional
    public Site update(UUID id, SiteCommand c) {
        Site current = get(id);
        Versions.check(c.version(), current.version());
        if (!current.organizationId().equals(c.organizationId())) {
            throw new BusinessRuleException("organizationId", "Площадку нельзя перенести в другую организацию");
        }
        Site site = new Site(id, current.version(), current.organizationId(), c.name(), c.code(), c.address(),
                c.active() == null || c.active());
        ensureNameIsFree(site);
        return sites.save(site);
    }

    private void ensureNameIsFree(Site site) {
        sites.findByOrganization(site.organizationId(), true).stream()
                .filter(o -> !o.id().equals(site.id()) && o.name().equalsIgnoreCase(site.name()))
                .findFirst()
                .ifPresent(o -> {
                    throw new ConflictException("У организации уже есть площадка «" + o.name() + "»");
                });
        if (site.code() != null) {
            sites.findByOrganization(site.organizationId(), true).stream()
                    .filter(o -> !o.id().equals(site.id()) && site.code().equals(o.code()))
                    .findFirst()
                    .ifPresent(o -> {
                        throw new ConflictException("Код «" + site.code() + "» уже у площадки «" + o.name() + "»");
                    });
        }
    }
}
