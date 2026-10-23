package com.company.acts.act.adapter.out.security;

import com.company.acts.act.application.port.out.CurrentUserProvider;
import com.company.acts.act.domain.CurrentUser;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;

/** Текущий пользователь из токена Keycloak: id (sub), логин и роли. */
@Component
class SecurityCurrentUserProvider implements CurrentUserProvider {

    @Override
    public CurrentUser current() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null) {
            return new CurrentUser("system", "system", Set.of());
        }
        Set<String> roles = auth.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .filter(a -> a.startsWith("ROLE_"))
                .map(a -> a.substring("ROLE_".length()))
                .collect(Collectors.toUnmodifiableSet());
        if (auth instanceof JwtAuthenticationToken jwt) {
            return new CurrentUser(jwt.getToken().getSubject(), auth.getName(), roles);
        }
        return new CurrentUser(auth.getName(), auth.getName(), roles);
    }
}
