package com.company.acts.directory.adapter.out.client;

import com.company.acts.directory.application.port.out.IdentityProvider;
import com.company.acts.directory.domain.EngineerRole;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/** Заглушка для тестов и запуска без Keycloak ({@code app.identity.keycloak.enabled=false}). */
@Component
@ConditionalOnProperty(name = "app.identity.keycloak.enabled", havingValue = "false")
class LocalIdentityAdapter implements IdentityProvider {

    private static final Logger log = LoggerFactory.getLogger(LocalIdentityAdapter.class);

    @Override
    public String createUser(String username, String temporaryPassword, String firstName, String lastName,
                             String email, EngineerRole role) {
        log.warn("Keycloak отключён: учётная запись {} не создана", username);
        return "local-" + UUID.randomUUID();
    }

    @Override
    public void updateUser(String userId, String firstName, String lastName, String email, boolean enabled) {
        // Keycloak отключён — синхронизировать нечего.
    }

    @Override
    public void deleteUser(String userId) {
        // Keycloak отключён — удалять нечего.
    }
}
