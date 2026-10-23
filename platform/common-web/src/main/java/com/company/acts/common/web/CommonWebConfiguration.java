package com.company.acts.common.web;

import com.company.acts.common.web.error.GlobalExceptionHandler;
import com.company.acts.common.web.security.KeycloakJwtAuthenticationConverter;
import com.company.acts.common.web.system.SystemInfoController;
import com.company.acts.common.web.tracing.CorrelationIdFilter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Общая конфигурация всех сервлетных сервисов. Подключается в каждом сервисе через
 * {@code @Import(CommonWebConfiguration.class)} — явно, без авто-конфигурации,
 * чтобы не зависеть от порядка авто-конфигураций Spring Boot.
 */
@Configuration(proxyBeanMethods = false)
@Import({GlobalExceptionHandler.class, SystemInfoController.class})
public class CommonWebConfiguration {

    /** Пути, доступные без токена: проверки живости и метрики для Prometheus. */
    static final String[] PUBLIC_PATHS = {
            "/actuator/health", "/actuator/health/**", "/actuator/info", "/actuator/prometheus"
    };

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(a -> a
                        .requestMatchers(PUBLIC_PATHS).permitAll()
                        .anyRequest().authenticated())
                .oauth2ResourceServer(o -> o.jwt(j -> j.jwtAuthenticationConverter(new KeycloakJwtAuthenticationConverter())));
        return http.build();
    }

    @Bean
    CorrelationIdFilter correlationIdFilter() {
        return new CorrelationIdFilter();
    }
}
