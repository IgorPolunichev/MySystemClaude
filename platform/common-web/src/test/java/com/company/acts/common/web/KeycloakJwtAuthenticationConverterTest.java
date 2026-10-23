package com.company.acts.common.web;

import static org.assertj.core.api.Assertions.assertThat;

import com.company.acts.common.web.security.KeycloakJwtAuthenticationConverter;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;

class KeycloakJwtAuthenticationConverterTest {

    private final KeycloakJwtAuthenticationConverter converter = new KeycloakJwtAuthenticationConverter();

    @Test
    void mapsRealmRolesAndUsername() {
        Jwt jwt = jwt(Map.of(
                "sub", "42",
                "preferred_username", "ivanov",
                "realm_access", Map.of("roles", List.of("ENGINEER", "ADMIN"))));

        var auth = converter.convert(jwt);

        assertThat(auth.getName()).isEqualTo("ivanov");
        assertThat(auth.getAuthorities()).extracting(GrantedAuthority::getAuthority)
                .containsExactlyInAnyOrder("ROLE_ENGINEER", "ROLE_ADMIN");
    }

    @Test
    void worksWithoutRoles() {
        var auth = converter.convert(jwt(Map.of("sub", "42")));

        assertThat(auth.getName()).isEqualTo("42");
        assertThat(auth.getAuthorities()).isEmpty();
    }

    private static Jwt jwt(Map<String, Object> claims) {
        return Jwt.withTokenValue("token")
                .header("alg", "RS256")
                .claims(c -> c.putAll(claims))
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(60))
                .build();
    }
}
