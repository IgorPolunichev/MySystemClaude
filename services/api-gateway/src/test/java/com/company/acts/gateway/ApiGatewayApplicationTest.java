package com.company.acts.gateway;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cloud.gateway.route.RouteLocator;

@SpringBootTest
class ApiGatewayApplicationTest {

    @Autowired
    RouteLocator routeLocator;

    @Test
    void allServiceRoutesAreRegistered() {
        var ids = routeLocator.getRoutes().map(r -> r.getId()).collectList().block();
        assertThat(ids).contains(
                "directory-service", "parts-service", "act-service", "media-service",
                "document-service", "analytics-service", "mail-service",
                "act-service-system");
    }
}
