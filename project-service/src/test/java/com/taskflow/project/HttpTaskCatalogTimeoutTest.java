package com.taskflow.project;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.SocketTimeoutException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import com.sun.net.httpserver.HttpServer;
import com.taskflow.project.client.TaskCatalog;
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
        "spring.datasource.url=jdbc:h2:mem:task-timeout-test",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.datasource.driver-class-name=org.h2.Driver"
})
class HttpTaskCatalogTimeoutTest {
    private static final long BACKEND_RESPONSE_DELAY_MILLISECONDS = 4000;
    private static final ExecutorService backendExecutor = Executors.newVirtualThreadPerTaskExecutor();
    private static final HttpServer backend = startBackend();

    @Autowired
    private TaskCatalog taskCatalog;

    @DynamicPropertySource
    static void backendInstance(DynamicPropertyRegistry registry) {
        registry.add("spring.cloud.discovery.client.simple.instances.task-service[0].uri",
                () -> "http://127.0.0.1:" + backend.getAddress().getPort());
    }

    @AfterAll
    static void stopBackend() {
        backend.stop(0);
        backendExecutor.shutdownNow();
    }

    @Test
    @Timeout(10)
    void interruptsASlowTaskServiceAndReturnsServiceUnavailable() {
        assertThatThrownBy(() -> taskCatalog.requireExistingTask(42L))
                .isInstanceOfSatisfying(ResponseStatusException.class, exception ->
                        assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE))
                .hasRootCauseInstanceOf(SocketTimeoutException.class);
    }

    private static HttpServer startBackend() {
        try {
            HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            server.setExecutor(backendExecutor);
            server.createContext("/tasks/42", exchange -> {
                try {
                    Thread.sleep(BACKEND_RESPONSE_DELAY_MILLISECONDS);
                    exchange.sendResponseHeaders(200, -1);
                } catch (InterruptedException exception) {
                    Thread.currentThread().interrupt();
                } finally {
                    exchange.close();
                }
            });
            server.start();
            return server;
        } catch (IOException exception) {
            throw new IllegalStateException("Could not start timeout test backend", exception);
        }
    }
}
