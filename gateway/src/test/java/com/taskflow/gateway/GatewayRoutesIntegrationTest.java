package com.taskflow.gateway;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.actuate.observability.AutoConfigureObservability;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.reactive.server.WebTestClient;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

@AutoConfigureObservability
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "eureka.client.enabled=false",
        "management.zipkin.tracing.export.enabled=false",
        "management.tracing.sampling.probability=1.0",
        "management.tracing.propagation.type=W3C",
        "spring.cloud.gateway.server.webflux.metrics.enabled=true",
        "spring.cloud.gateway.server.webflux.observability.enabled=true",
        "resilience4j.circuitbreaker.instances.taskServiceCircuitBreaker.slidingWindowSize=4",
        "resilience4j.circuitbreaker.instances.taskServiceCircuitBreaker.minimumNumberOfCalls=2",
        "resilience4j.circuitbreaker.instances.taskServiceCircuitBreaker.failureRateThreshold=50",
        "resilience4j.circuitbreaker.instances.taskServiceCircuitBreaker.waitDurationInOpenState=10s"
})
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class GatewayRoutesIntegrationTest {

    private static final AtomicBoolean failBackends = new AtomicBoolean(false);
    private static final AtomicReference<String> observedTraceparent = new AtomicReference<>();
    private static final AtomicReference<String> observedTraceHeaders = new AtomicReference<>();
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
    void exposesMetricsAndPrometheusEndpoints() {
        webTestClient.get()
                .uri("/actuator/metrics")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.names").isArray();

        webTestClient.get()
                .uri("/actuator/prometheus")
                .exchange()
                .expectStatus().isOk()
                .expectBody(String.class)
                .consumeWith(response -> org.assertj.core.api.Assertions.assertThat(response.getResponseBody())
                        .contains("jvm_memory_used_bytes")
                        .contains("http_server_requests_seconds_bucket"));
    }

    @Test
    @Order(2)
    void createsAndPropagatesTraceContextToDownstreamService() {
        observedTraceparent.set(null);

        webTestClient.get()
                .uri("/api/v1/tasks")
                .exchange()
                .expectStatus().isOk();

        org.assertj.core.api.Assertions.assertThat(observedTraceparent.get())
                .as("Downstream trace headers: %s", observedTraceHeaders.get())
                .matches("00-[0-9a-f]{32}-[0-9a-f]{16}-01");
    }

    @Test
    @Order(3)
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
    @Order(4)
    void routesReadinessProbeToTaskService() {
        webTestClient.get()
                .uri("/api/v1/health/readiness")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.path").isEqualTo("/actuator/health/readiness");
    }

    @Test
    @Order(5)
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
                String receivedTraceparent = exchange.getRequestHeaders().getFirst("traceparent");
                observedTraceHeaders.set("traceparent=" + receivedTraceparent
                        + ", b3=" + exchange.getRequestHeaders().getFirst("b3")
                        + ", X-B3-TraceId=" + exchange.getRequestHeaders().getFirst("X-B3-TraceId"));
                if (receivedTraceparent != null) {
                    observedTraceparent.set(receivedTraceparent);
                }
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
