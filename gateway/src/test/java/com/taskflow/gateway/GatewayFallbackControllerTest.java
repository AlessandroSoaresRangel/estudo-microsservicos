package com.taskflow.gateway;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.HttpMethod;
import org.springframework.test.web.reactive.server.WebTestClient;

class GatewayFallbackControllerTest {
    @ParameterizedTest
    @ValueSource(strings = {"POST", "PUT", "PATCH", "DELETE"})
    void preservesServiceUnavailableForWriteRequests(String method) {
        WebTestClient.bindToController(new GatewayFallbackController()).build()
                .method(HttpMethod.valueOf(method))
                .uri("/fallback/tasks")
                .exchange()
                .expectStatus().isEqualTo(503)
                .expectBody().jsonPath("$.error").isEqualTo("task-service-unavailable");
    }
}
