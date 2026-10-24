package com.company.acts.analytics.adapter.out.security;

import com.company.acts.analytics.application.port.out.CurrentUserProvider;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;

@Component
class SecurityCurrentUserProvider implements CurrentUserProvider {

    @Override
    public CurrentUser current() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null) {
            return new CurrentUser("system", "system", Set.of());
        }
        Set<String> roles = auth.getAuthorities().stream().map(GrantedAuthority::getAuthority)
                .filter(a -> a.startsWith("ROLE_")).map(a -> a.substring(5)).collect(Collectors.toUnmodifiableSet());
        String id = auth instanceof JwtAuthenticationToken jwt ? jwt.getToken().getSubject() : auth.getName();
        return new CurrentUser(id, auth.getName(), roles);
    }
}
