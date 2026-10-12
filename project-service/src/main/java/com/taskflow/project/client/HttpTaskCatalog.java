package com.taskflow.project.client;

import org.springframework.cloud.client.loadbalancer.LoadBalanced;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.server.ResponseStatusException;

@Component
public class HttpTaskCatalog implements TaskCatalog {
    private final RestClient restClient;

    public HttpTaskCatalog(@LoadBalanced RestClient.Builder builder) {
        this.restClient = builder.baseUrl("http://task-service").build();
    }

    @Override
    public void requireExistingTask(Long taskId) {
        try {
            restClient.get().uri("/tasks/{id}", taskId).retrieve().toBodilessEntity();
        } catch (RestClientResponseException exception) {
            if (exception.getStatusCode().value() == HttpStatus.NOT_FOUND.value()) {
                throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
                        "Tarefa " + taskId + " não encontrada.", exception);
            }
            throw unavailable(exception);
        } catch (RestClientException exception) {
            throw unavailable(exception);
        }
    }

    private ResponseStatusException unavailable(RestClientException cause) {
        return new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                "Não foi possível validar as tarefas. Tente novamente.", cause);
    }
}
