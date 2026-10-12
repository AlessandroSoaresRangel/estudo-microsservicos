package com.taskflow.project;

import com.taskflow.project.client.HttpTaskCatalog;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import org.springframework.web.server.ResponseStatusException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;

class HttpTaskCatalogTest {
    private MockRestServiceServer server;
    private HttpTaskCatalog taskCatalog;

    @BeforeEach
    void configureClient() {
        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        taskCatalog = new HttpTaskCatalog(builder);
    }

    @Test
    void acceptsAnExistingTask() {
        server.expect(requestTo("http://task-service/tasks/42")).andRespond(withStatus(HttpStatus.OK));
        taskCatalog.requireExistingTask(42L);
        server.verify();
    }

    @Test
    void translatesAMissingTaskToAnInvalidLink() {
        server.expect(requestTo("http://task-service/tasks/42")).andRespond(withStatus(HttpStatus.NOT_FOUND));
        assertThatThrownBy(() -> taskCatalog.requireExistingTask(42L))
                .isInstanceOfSatisfying(ResponseStatusException.class, exception ->
                        assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY));
        server.verify();
    }

    @Test
    void translatesTaskServiceFailureToServiceUnavailable() {
        server.expect(requestTo("http://task-service/tasks/42"))
                .andRespond(withStatus(HttpStatus.INTERNAL_SERVER_ERROR));
        assertThatThrownBy(() -> taskCatalog.requireExistingTask(42L))
                .isInstanceOfSatisfying(ResponseStatusException.class, exception ->
                        assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE));
        server.verify();
    }
}
