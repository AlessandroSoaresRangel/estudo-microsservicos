package com.taskflow.gateway;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.reactive.server.WebTestClient;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicBoolean;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "eureka.client.enabled=false",
        "resilience4j.circuitbreaker.instances.taskServiceCircuitBreaker.slidingWindowSize=4",
        "resilience4j.circuitbreaker.instances.taskServiceCircuitBreaker.minimumNumberOfCalls=2",
        "resilience4j.circuitbreaker.instances.taskServiceCircuitBreaker.failureRateThreshold=50",
        "resilience4j.circuitbreaker.instances.taskServiceCircuitBreaker.waitDurationInOpenState=10s"
})
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class GatewayRoutesIntegrationTest {

    private static final AtomicBoolean failBackends = new AtomicBoolean(false);
    private static final HttpServer backendOne = startBackend("one");
    private static final HttpServer backendTwo = startBackend("two");

    @Autowired
    private WebTestClient webTestClient;

    @DynamicPropertySource
    static void backendInstances(DynamicPropertyRegistry registry) {
        registry.add("spring.cloud.discovery.client.simple.instances.task-service[0].uri",
                () -> backendUri(backendOne));
        registry.add("spring.cloud.discovery.client.simple.instances.task-service[1].uri",
                () -> "http://127.0.0.1:1");
        registry.add("spring.cloud.discovery.client.simple.instances.task-service[2].uri",
                () -> backendUri(backendTwo));
    }

    @AfterAll
    static void stopBackends() {
        backendOne.stop(0);
        backendTwo.stop(0);
    }

    @Test
    @Order(1)
    void routesTasksAndStripsVersionPrefixWhileRetryingUnavailableInstance() {
        for (int i = 0; i < 9; i++) {
            webTestClient.get()
                    .uri("/api/v1/tasks")
                    .exchange()
                    .expectStatus().isOk()
                    .expectBody()
                    .jsonPath("$.path").isEqualTo("/tasks");
        }
    }

    @Test
    @Order(2)
    void routesReadinessProbeToTaskService() {
        webTestClient.get()
                .uri("/api/v1/health/readiness")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.path").isEqualTo("/actuator/health/readiness");
    }

    @Test
    @Order(3)
    void opensCircuitAndUsesFallbackAfterRepeatedBackendFailures() {
        failBackends.set(true);
        try {
            for (int i = 0; i < 2; i++) {
                webTestClient.get()
                        .uri("/api/v1/tasks")
                        .exchange()
                        .expectStatus().isEqualTo(503);
            }

            webTestClient.get()
                    .uri("/api/v1/tasks")
                    .exchange()
                    .expectStatus().isEqualTo(503)
                    .expectBody()
                    .jsonPath("$.error").isEqualTo("task-service-unavailable");
        } finally {
            failBackends.set(false);
        }
    }

    private static HttpServer startBackend(String name) {
        try {
            HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            server.createContext("/", exchange -> {
                boolean failing = failBackends.get();
                String body = failing
                        ? "{\"error\":\"backend-unavailable\"}"
                        : "{\"instance\":\"" + name + "\",\"path\":\""
                                + exchange.getRequestURI().getPath() + "\"}";
                byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
                exchange.getResponseHeaders().add("Content-Type", "application/json");
                exchange.sendResponseHeaders(failing ? 503 : 200, bytes.length);
                try (var response = exchange.getResponseBody()) {
                    response.write(bytes);
                }
            });
            server.start();
            return server;
        } catch (IOException exception) {
            throw new IllegalStateException("Could not start test backend", exception);
        }
    }

    private static String backendUri(HttpServer server) {
        return "http://127.0.0.1:" + server.getAddress().getPort();
    }
}
