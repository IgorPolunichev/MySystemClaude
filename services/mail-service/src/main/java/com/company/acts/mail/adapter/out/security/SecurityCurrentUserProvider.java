package com.company.acts.mail.adapter.out.security;

import com.company.acts.mail.application.port.out.CurrentUserProvider;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;

/** Пользователь из токена: логин, ФИО (claim name) и почта (claim email) — для подписи и Reply-To. */
@Component
class SecurityCurrentUserProvider implements CurrentUserProvider {

    @Override
    public CurrentUser current() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null) {
            return new CurrentUser("system", null, null, Set.of());
        }
        Set<String> roles = auth.getAuthorities().stream().map(GrantedAuthority::getAuthority)
                .filter(a -> a.startsWith("ROLE_")).map(a -> a.substring(5)).collect(Collectors.toUnmodifiableSet());
        String name = null;
        String email = null;
        if (auth instanceof JwtAuthenticationToken jwt) {
            name = jwt.getToken().getClaimAsString("name");
            email = jwt.getToken().getClaimAsString("email");
        }
        return new CurrentUser(auth.getName(), name, email, roles);
    }
}
