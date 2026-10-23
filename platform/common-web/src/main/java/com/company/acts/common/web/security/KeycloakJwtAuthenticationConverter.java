package com.company.acts.common.web.security;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

/**
 * Превращает роли Keycloak из claim {@code realm_access.roles} в authorities вида {@code ROLE_ENGINEER}.
 * Имя пользователя берётся из {@code preferred_username}.
 */
public class KeycloakJwtAuthenticationConverter implements Converter<Jwt, AbstractAuthenticationToken> {

    public static final String ROLE_PREFIX = "ROLE_";

    @Override
    public AbstractAuthenticationToken convert(Jwt jwt) {
        String username = jwt.getClaimAsString("preferred_username");
        return new JwtAuthenticationToken(jwt, extractAuthorities(jwt), username != null ? username : jwt.getSubject());
    }

    static Collection<GrantedAuthority> extractAuthorities(Jwt jwt) {
        Object realmAccess = jwt.getClaims().get("realm_access");
        if (!(realmAccess instanceof Map<?, ?> map)) {
            return List.of();
        }
        Object roles = map.get("roles");
        if (!(roles instanceof Collection<?> roleList)) {
            return List.of();
        }
        return roleList.stream()
                .map(String::valueOf)
                .filter(role -> !role.isBlank())
                .<GrantedAuthority>map(role -> new SimpleGrantedAuthority(ROLE_PREFIX + role))
                .toList();
    }
}
