package com.company.acts.directory.adapter.out.client;

import com.company.acts.common.core.ConflictException;
import com.company.acts.directory.application.port.out.IdentityProvider;
import com.company.acts.directory.domain.EngineerRole;
import java.net.URI;
import java.net.http.HttpClient;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;

/**
 * Учётные записи в Keycloak через Admin REST API. Сервис входит под собственным клиентом
 * {@code acts-directory} (client credentials) с правами realm-management: manage-users, view-users.
 */
@Component
@ConditionalOnProperty(name = "app.identity.keycloak.enabled", havingValue = "true", matchIfMissing = true)
class KeycloakIdentityAdapter implements IdentityProvider {

    private static final Logger log = LoggerFactory.getLogger(KeycloakIdentityAdapter.class);

    private final RestClient client;
    private final String realm;
    private final String clientId;
    private final String clientSecret;

    private String accessToken;
    private Instant tokenExpiresAt = Instant.EPOCH;

    KeycloakIdentityAdapter(@Value("${app.identity.keycloak.base-url}") String baseUrl,
                            @Value("${app.identity.keycloak.realm}") String realm,
                            @Value("${app.identity.keycloak.client-id}") String clientId,
                            @Value("${app.identity.keycloak.client-secret}") String clientSecret) {
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(
                HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build());
        factory.setReadTimeout(Duration.ofSeconds(10));
        this.client = RestClient.builder().baseUrl(baseUrl).requestFactory(factory).build();
        this.realm = realm;
        this.clientId = clientId;
        this.clientSecret = clientSecret;
    }

    @Override
    public String createUser(String username, String temporaryPassword, String firstName, String lastName,
                             String email, EngineerRole role) {
        Map<String, Object> user = new HashMap<>();
        user.put("username", username);
        user.put("enabled", true);
        user.put("firstName", firstName);
        user.put("lastName", lastName);
        if (email != null) {
            user.put("email", email);
            user.put("emailVerified", true);
        }
        user.put("credentials", List.of(Map.of("type", "password", "value", temporaryPassword, "temporary", true)));
        URI location;
        try {
            location = client.post().uri("/admin/realms/{realm}/users", realm)
                    .headers(h -> h.setBearerAuth(token()))
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(user)
                    .retrieve()
                    .toBodilessEntity()
                    .getHeaders().getLocation();
        } catch (HttpClientErrorException.Conflict e) {
            throw new ConflictException("В Keycloak уже есть пользователь с логином «" + username + "» или такой почтой");
        }
        if (location == null) {
            throw new IllegalStateException("Keycloak не вернул адрес созданного пользователя");
        }
        String path = location.getPath();
        String userId = path.substring(path.lastIndexOf('/') + 1);

        Map<?, ?> roleRepresentation = client.get().uri("/admin/realms/{realm}/roles/{role}", realm, role.name())
                .headers(h -> h.setBearerAuth(token()))
                .retrieve()
                .body(Map.class);
        client.post().uri("/admin/realms/{realm}/users/{id}/role-mappings/realm", realm, userId)
                .headers(h -> h.setBearerAuth(token()))
                .contentType(MediaType.APPLICATION_JSON)
                .body(List.of(roleRepresentation))
                .retrieve()
                .toBodilessEntity();
        log.info("Создана учётная запись Keycloak {} ({}) с ролью {}", username, userId, role);
        return userId;
    }

    @Override
    public void updateUser(String userId, String firstName, String lastName, String email, boolean enabled) {
        Map<String, Object> user = new HashMap<>();
        user.put("firstName", firstName);
        user.put("lastName", lastName);
        user.put("enabled", enabled);
        if (email != null) {
            user.put("email", email);
        }
        client.put().uri("/admin/realms/{realm}/users/{id}", realm, userId)
                .headers(h -> h.setBearerAuth(token()))
                .contentType(MediaType.APPLICATION_JSON)
                .body(user)
                .retrieve()
                .toBodilessEntity();
    }

    @Override
    public void deleteUser(String userId) {
        try {
            client.delete().uri("/admin/realms/{realm}/users/{id}", realm, userId)
                    .headers(h -> h.setBearerAuth(token()))
                    .retrieve()
                    .toBodilessEntity();
        } catch (RuntimeException e) {
            log.error("Не удалось удалить учётную запись Keycloak {} при откате", userId, e);
        }
    }

    /** Токен сервисной учётной записи; переиспользуется до истечения срока. */
    private synchronized String token() {
        if (accessToken != null && Instant.now().isBefore(tokenExpiresAt)) {
            return accessToken;
        }
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("grant_type", "client_credentials");
        form.add("client_id", clientId);
        form.add("client_secret", clientSecret);
        Map<?, ?> response = client.post().uri("/realms/{realm}/protocol/openid-connect/token", realm)
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body(form)
                .retrieve()
                .body(Map.class);
        if (response == null || response.get("access_token") == null) {
            throw new IllegalStateException("Keycloak не выдал токен сервисной учётной записи");
        }
        accessToken = String.valueOf(response.get("access_token"));
        long expiresIn = response.get("expires_in") instanceof Number n ? n.longValue() : 60;
        tokenExpiresAt = Instant.now().plusSeconds(Math.max(expiresIn - 30, 10));
        return accessToken;
    }
}
