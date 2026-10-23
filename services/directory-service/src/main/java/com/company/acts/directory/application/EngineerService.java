package com.company.acts.directory.application;

import com.company.acts.common.core.BusinessRuleException;
import com.company.acts.common.core.ConflictException;
import com.company.acts.common.core.NotFoundException;
import com.company.acts.common.core.PageQuery;
import com.company.acts.common.core.PageResult;
import com.company.acts.common.core.Uuid7;
import com.company.acts.directory.application.command.EngineerCreateCommand;
import com.company.acts.directory.application.command.EngineerUpdateCommand;
import com.company.acts.directory.application.port.out.EngineerRepository;
import com.company.acts.directory.application.port.out.IdentityProvider;
import com.company.acts.directory.application.port.out.ServiceDepartmentRepository;
import com.company.acts.directory.application.view.EngineerView;
import com.company.acts.directory.domain.Engineer;
import java.util.Locale;
import java.util.UUID;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Инженеры и их учётные записи. Учётная запись создаётся в Keycloak одновременно с карточкой
 * инженера (решение этапа 0 № 9): администратор задаёт логин, роль и временный пароль.
 */
@Service
public class EngineerService {

    private final EngineerRepository engineers;
    private final ServiceDepartmentRepository serviceDepartments;
    private final IdentityProvider identityProvider;

    public EngineerService(EngineerRepository engineers, ServiceDepartmentRepository serviceDepartments,
                           IdentityProvider identityProvider) {
        this.engineers = engineers;
        this.serviceDepartments = serviceDepartments;
        this.identityProvider = identityProvider;
    }

    @Transactional(readOnly = true)
    public PageResult<EngineerView> search(String text, UUID serviceDepartmentId, Boolean active, PageQuery page) {
        return engineers.search(text, serviceDepartmentId, active, page);
    }

    @Transactional(readOnly = true)
    public EngineerView get(UUID id) {
        return engineers.findViewById(id).orElseThrow(() -> NotFoundException.of("Инженер", id));
    }

    /**
     * Карточка текущего пользователя. Ищется по id в Keycloak, затем по логину; при совпадении по
     * логину связь с Keycloak запоминается.
     */
    @Transactional
    public EngineerView current(String keycloakUserId, String username) {
        Engineer engineer = engineers.findByKeycloakUserId(keycloakUserId)
                .or(() -> username == null ? java.util.Optional.empty()
                        : engineers.findByUsername(username.toLowerCase(Locale.ROOT)))
                .orElseThrow(() -> new NotFoundException(
                        "Для вашей учётной записи нет карточки инженера. Обратитесь к администратору."));
        if (!keycloakUserId.equals(engineer.keycloakUserId())) {
            engineers.save(engineer.withKeycloakUserId(keycloakUserId));
        }
        return get(engineer.id());
    }

    @PreAuthorize(Roles.ADMIN)
    @Transactional
    public EngineerView create(EngineerCreateCommand c) {
        if (c.id() != null && engineers.findById(c.id()).isPresent()) {
            return get(c.id());
        }
        Engineer engineer = new Engineer(c.id() != null ? c.id() : Uuid7.next(), null, null, c.username(),
                c.lastName(), c.firstName(), c.middleName(), c.position(), c.serviceDepartmentId(), c.phone(),
                c.email(), true);
        validateDepartment(engineer.serviceDepartmentId());
        if (engineers.findByUsername(engineer.username()).isPresent()) {
            throw new ConflictException("Логин «" + engineer.username() + "» уже занят");
        }
        String keycloakId = identityProvider.createUser(engineer.username(), c.initialPassword(),
                engineer.firstName(), engineer.lastName(), engineer.email(), c.role());
        try {
            engineers.save(engineer.withKeycloakUserId(keycloakId));
        } catch (RuntimeException e) {
            identityProvider.deleteUser(keycloakId);
            throw e;
        }
        return get(engineer.id());
    }

    @PreAuthorize(Roles.ADMIN)
    @Transactional
    public EngineerView update(UUID id, EngineerUpdateCommand c) {
        Engineer current = engineers.findById(id).orElseThrow(() -> NotFoundException.of("Инженер", id));
        Versions.check(c.version(), current.version());
        Engineer engineer = new Engineer(id, current.version(), current.keycloakUserId(), current.username(),
                c.lastName(), c.firstName(), c.middleName(), c.position(), c.serviceDepartmentId(), c.phone(),
                c.email(), c.active());
        validateDepartment(engineer.serviceDepartmentId());
        engineers.save(engineer);
        if (engineer.keycloakUserId() != null) {
            identityProvider.updateUser(engineer.keycloakUserId(), engineer.firstName(), engineer.lastName(),
                    engineer.email(), engineer.active());
        }
        return get(id);
    }

    private void validateDepartment(UUID serviceDepartmentId) {
        if (serviceDepartmentId != null && serviceDepartments.findById(serviceDepartmentId).isEmpty()) {
            throw new BusinessRuleException("serviceDepartmentId", "Отдел не найден");
        }
    }
}
