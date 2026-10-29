package com.taskflow.task.client;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.SocketTimeoutException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicReference;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.web.server.ResponseStatusException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest(properties = {
        "eureka.client.enabled=false",
        "management.tracing.enabled=false",
        "taskflow.task-link-cleanup.enabled=false",
        "spring.datasource.url=jdbc:h2:mem:project-client-test",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.datasource.driver-class-name=org.h2.Driver"
})
class HttpProjectCatalogIntegrationTest {
    private static final long RESPONSE_DELAY_MILLISECONDS = 4000;
    private static final AtomicReference<String> lastRequest = new AtomicReference<>();
    private static final ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();
    private static final HttpServer backend = startBackend();

    @Autowired
    private ProjectCatalog projectCatalog;

    @DynamicPropertySource
    static void backendInstance(DynamicPropertyRegistry registry) {
        registry.add("spring.cloud.discovery.client.simple.instances.project-service[0].uri",
                () -> "http://127.0.0.1:" + backend.getAddress().getPort());
    }

    @AfterAll
    static void stopBackend() {
        backend.stop(0);
        executor.shutdownNow();
    }

    @Test
    void assignsTheTaskUsingFeignAndServiceDiscovery() {
        projectCatalog.assignTask(1L, 42L);
        assertThat(lastRequest.get()).isEqualTo("POST /internal/project-task-links/1/42 ");
    }

    @Test
    void rejectsMissingProjects() {
        assertRemoteStatus(999L, HttpStatus.UNPROCESSABLE_ENTITY);
    }

    @Test
    void rejectsTasksOwnedByAnotherProject() {
        assertRemoteStatus(2L, HttpStatus.CONFLICT);
    }

    @Test
    void reportsRemoteFailure() {
        assertRemoteStatus(3L, HttpStatus.SERVICE_UNAVAILABLE);
    }

    @Test
    @Timeout(10)
    void interruptsASlowProjectService() {
        assertThatThrownBy(() -> projectCatalog.assignTask(4L, 42L))
                .isInstanceOfSatisfying(ResponseStatusException.class, exception ->
                        assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE))
                .hasRootCauseInstanceOf(SocketTimeoutException.class);
    }

    @Test
    void releasesTheLinkOfADeletedTask() {
        projectCatalog.releaseTask(42L);
        assertThat(lastRequest.get()).isEqualTo("DELETE /internal/project-task-links/42 ");
    }

    private void assertRemoteStatus(Long projectId, HttpStatus status) {
        assertThatThrownBy(() -> projectCatalog.assignTask(projectId, 42L))
                .isInstanceOfSatisfying(ResponseStatusException.class, exception ->
                        assertThat(exception.getStatusCode()).isEqualTo(status));
    }

    private static HttpServer startBackend() {
        try {
            HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            server.setExecutor(executor);
            server.createContext("/internal/project-task-links", exchange -> {
                String path = exchange.getRequestURI().getPath();
                String body = new String(exchange.getRequestBody().readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
                lastRequest.set(exchange.getRequestMethod() + " " + path + " " + body);
                try {
                    if (path.endsWith("/4/42")) {
                        Thread.sleep(RESPONSE_DELAY_MILLISECONDS);
                    }
                    int status = switch (path) {
                        case "/internal/project-task-links/999/42" -> 404;
                        case "/internal/project-task-links/2/42" -> 409;
                        case "/internal/project-task-links/3/42" -> 500;
                        default -> 204;
                    };
                    exchange.sendResponseHeaders(status, -1);
                } catch (InterruptedException exception) {
                    Thread.currentThread().interrupt();
                } finally {
                    exchange.close();
                }
            });
            server.start();
            return server;
        } catch (IOException exception) {
            throw new IllegalStateException("Could not start project test backend", exception);
        }
    }
}
