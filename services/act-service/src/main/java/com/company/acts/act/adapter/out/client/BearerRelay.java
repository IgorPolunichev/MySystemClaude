package com.company.acts.act.adapter.out.client;

import java.net.http.HttpClient;
import java.time.Duration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.client.RestClient;

/**
 * Вызовы других сервисов выполняются от имени пользователя: его токен передаётся дальше, и сервис-получатель
 * сам проверяет права.
 */
final class BearerRelay {

    private BearerRelay() {
    }

    static RestClient client(String baseUrl) {
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(
                HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build());
        factory.setReadTimeout(Duration.ofSeconds(10));
        return RestClient.builder()
                .baseUrl(baseUrl)
                .requestFactory(factory)
                .requestInterceptor((request, body, execution) -> {
                    Authentication auth = SecurityContextHolder.getContext().getAuthentication();
                    if (auth instanceof JwtAuthenticationToken jwt) {
                        request.getHeaders().set(HttpHeaders.AUTHORIZATION, "Bearer " + jwt.getToken().getTokenValue());
                    }
                    return execution.execute(request, body);
                })
                .build();
    }
}
