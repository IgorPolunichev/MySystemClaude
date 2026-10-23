package com.company.acts.common.web.system;

import java.time.Instant;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Служебный эндпоинт: показывает, что сервис жив и принимает токен пользователя.
 * Через api-gateway доступен как {@code /api/v1/system/{service}/info}.
 */
@RestController
public class SystemInfoController {

    private final String serviceName;
    private final String version;

    public SystemInfoController(@Value("${spring.application.name}") String serviceName,
                                @Value("${app.version:dev}") String version) {
        this.serviceName = serviceName;
        this.version = version;
    }

    @GetMapping("/api/v1/system/info")
    public SystemInfo info(Authentication authentication) {
        List<String> roles = authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .filter(a -> a.startsWith("ROLE_"))
                .map(a -> a.substring("ROLE_".length()))
                .sorted()
                .toList();
        return new SystemInfo(serviceName, version, Instant.now(), authentication.getName(), roles);
    }

    public record SystemInfo(String service, String version, Instant serverTime, String user, List<String> roles) {
    }
}
